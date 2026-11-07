package com.cidas.edudoc.template.application;

import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.domain.FormatRuleMatcher;
import com.cidas.edudoc.template.domain.IssueDraft;
import com.cidas.edudoc.template.domain.StyleSnapshot;
import com.cidas.edudoc.template.infrastructure.DocxStyleExtractor;
import com.cidas.edudoc.template.infrastructure.FormatCheckProperties;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 格式校验 worker（D-04）：消费 `edudoc:stream:format-check`。
 * 流程：CAS QUEUED→RUNNING → 下载 → D-03 结构树联动重建 → 样式快照 → 规则比对 → 单事务回写。
 * 骨架沿用 DocumentParseWorker（组幂等、pid consumer 名、XACK 兜底）。
 * ponytail: 无 DLQ/pending 回收；失败落 ERROR 由「重新发起校验」重试，需要时加 XAUTOCLAIM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormatCheckWorker {

    public static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final StringRedisTemplate redis;
    private final OssObjectRepository ossObjectRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final DocxStyleExtractor styleExtractor;
    private final FormatRuleRepository ruleRepository;
    private final FormatRuleMatcher ruleMatcher;
    private final DocumentStructureService structureService;
    private final FormatCheckRepository checkRepository;
    private final FormatCheckTxService txService;
    private final FormatCheckProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean running;
    private ExecutorService pool;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || !started.compareAndSet(false, true)) {
            if (properties.enabled()) {
                log.info("格式校验 worker 已在运行，跳过重复启动");
            } else {
                log.info("格式校验 worker 已禁用（edudoc.format-check.enabled=false）");
            }
            return;
        }
        ensureGroup();
        running = true;
        pool = Executors.newFixedThreadPool(Math.max(1, properties.concurrency()), task -> {
            Thread thread = new Thread(task, "format-check-worker");
            thread.setDaemon(true);
            return thread;
        });
        String instance = hostName() + "-" + ProcessHandle.current().pid();
        for (int i = 0; i < Math.max(1, properties.concurrency()); i++) {
            int index = i;
            pool.submit(() -> consumeLoop("format-" + instance + "-" + index));
        }
        log.info("格式校验 worker 已启动：stream={} group={} concurrency={}",
                properties.streamKey(), properties.group(), properties.concurrency());
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(properties.streamKey(), ReadOffset.from("0"), properties.group());
        } catch (Exception e) {
            if (!isBusyGroup(e)) {
                throw e;
            }
        }
    }

    private static boolean isBusyGroup(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof io.lettuce.core.RedisBusyException
                    || String.valueOf(t.getMessage()).contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    private void consumeLoop(String consumerName) {
        var stream = redis.opsForStream();
        Consumer consumer = Consumer.from(properties.group(), consumerName);
        while (running) {
            List<MapRecord<String, Object, Object>> records;
            try {
                records = stream.read(consumer,
                        org.springframework.data.redis.connection.stream.StreamReadOptions.empty()
                                .count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed()));
            } catch (Exception e) {
                if (running) {
                    log.warn("格式校验 Stream 读取失败，2s 后重试：{}", e.getMessage());
                    sleep(2000);
                }
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (MapRecord<String, Object, Object> record : records) {
                handleRecord(stream, record);
            }
        }
    }

    private void handleRecord(org.springframework.data.redis.core.StreamOperations<String, Object, Object> stream,
                              MapRecord<String, Object, Object> record) {
        try {
            String payload = String.valueOf(record.getValue().get("payload"));
            var node = objectMapper.readTree(payload);
            process(node.path("checkId").asText(), node.path("documentId").asText(),
                    node.path("versionId").asText(), node.path("templateVersionId").asText(),
                    node.path("sourceObjectId").asText());
        } catch (Exception e) {
            log.error("格式校验事件处理失败 record={}", record.getId(), e);
        } finally {
            try {
                stream.acknowledge(properties.streamKey(), properties.group(), record.getId());
            } catch (Exception e) {
                log.warn("XACK 失败 record={}", record.getId(), e);
            }
        }
    }

    /** 事件体处理（可测试入口）：CAS 先行，重复/迟到投递直接跳过。 */
    void process(String checkId, String documentId, String versionId, String templateVersionId,
                 String sourceObjectId) {
        if (checkRepository.markRunning(checkId) == 0) {
            log.info("过期校验事件跳过 checkId={}", checkId);
            return;
        }
        try {
            byte[] bytes = download(sourceObjectId);
            structureService.parseAndPersist(bytes, versionId);
            StyleSnapshot snapshot = styleExtractor.extract(bytes);
            List<FormatRule> rules = ruleRepository.findByVersion(templateVersionId);
            List<IssueDraft> issues = ruleMatcher.match(rules, snapshot);
            int blocking = (int) issues.stream().filter(i -> "BLOCKER".equals(i.severity())).count();
            txService.writeResult(checkId, issues, issues.size(), blocking,
                    blocking == 0 ? "PASSED" : "FAILED", documentId, versionId);
        } catch (Exception e) {
            log.error("格式校验失败 checkId={}", checkId, e);
            txService.writeError(checkId, String.valueOf(e.getMessage()), documentId, versionId);
        }
    }

    private byte[] download(String sourceObjectId) {
        OssObject object = ossObjectRepository.findById(sourceObjectId)
                .orElseThrow(() -> new IllegalStateException("源对象登记不存在: " + sourceObjectId));
        Path temp = null;
        try {
            temp = Files.createTempFile("edudoc-format-check-", ".docx");
            ossStorageAdapter.downloadTo(object.getObjectKey(), temp);
            return Files.readAllBytes(temp);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("校验文件下载失败: " + e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (java.io.IOException ignored) {
                    // 临时文件清理失败不影响业务
                }
            }
        }
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "local";
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
