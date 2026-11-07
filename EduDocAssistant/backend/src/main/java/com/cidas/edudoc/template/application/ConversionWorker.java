package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.ConvertEngineGateway.ConvertResult;
import com.cidas.edudoc.template.application.impl.ConversionTxWriter;
import com.cidas.edudoc.template.infrastructure.ConversionProperties;
import com.cidas.edudoc.template.infrastructure.ConversionTask;
import com.cidas.edudoc.template.infrastructure.ConversionTaskRepository;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 格式转换 worker（D-06）：消费 `edudoc:stream:conversion`。
 * 流程：CAS QUEUED→RUNNING → 下载源 → ai-service /v1/convert → 结果入 OSS（role=CONVERTED，不建版本）
 * → 同事务终态+审计。骨架沿用 DocumentParseWorker。
 * ponytail: 无 DLQ；FAILED 任务由用户重新发起（保留记录可查原因，需求 §5.3.3.4）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversionWorker {

    private final StringRedisTemplate redis;
    private final OssObjectRepository ossObjectRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final ConvertEngineGateway engineGateway;
    private final ConversionTaskRepository taskRepository;
    private final ConversionTxWriter txWriter;
    private final ConversionProperties properties;
    private final OssProperties ossProperties;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean running;
    private ExecutorService pool;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || !started.compareAndSet(false, true)) {
            if (properties.enabled()) {
                log.info("转换 worker 已在运行，跳过重复启动");
            } else {
                log.info("转换 worker 已禁用（edudoc.conversion.enabled=false）");
            }
            return;
        }
        ensureGroup();
        running = true;
        pool = Executors.newFixedThreadPool(Math.max(1, properties.concurrency()), task -> {
            Thread thread = new Thread(task, "conversion-worker");
            thread.setDaemon(true);
            return thread;
        });
        String instance = hostName() + "-" + ProcessHandle.current().pid();
        for (int i = 0; i < Math.max(1, properties.concurrency()); i++) {
            int index = i;
            pool.submit(() -> consumeLoop("conv-" + instance + "-" + index));
        }
        log.info("转换 worker 已启动：stream={} group={} concurrency={}",
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
                    log.warn("转换 Stream 读取失败，2s 后重试：{}", e.getMessage());
                    sleep(2000);
                }
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (MapRecord<String, Object, Object> record : records) {
                try {
                    String payload = String.valueOf(record.getValue().get("payload"));
                    process(objectMapper.readTree(payload).path("taskId").asText());
                } catch (Exception e) {
                    log.error("转换事件处理失败 record={}", record.getId(), e);
                } finally {
                    try {
                        stream.acknowledge(properties.streamKey(), properties.group(), record.getId());
                    } catch (Exception e) {
                        log.warn("XACK 失败 record={}", record.getId(), e);
                    }
                }
            }
        }
    }

    /** 任务处理（可测试入口）。 */
    void process(String taskId) {
        if (taskRepository.markRunning(taskId) == 0) {
            log.info("过期转换事件跳过 taskId={}", taskId);
            return;
        }
        ConversionTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("转换任务行不存在 taskId={}", taskId);
            return;
        }
        try {
            OssObject source = ossObjectRepository.findById(task.getSourceObjectId())
                    .orElseThrow(() -> new ConversionEngineException("ENGINE_UNAVAILABLE", "源对象登记不存在"));
            ConvertResult result = engineGateway.convert(download(source), fileNameOf(source.getObjectKey()),
                    task.getTargetFormat());
            String objectKey = "edudoc/conversions/%s/%s.%s".formatted(monthDir(), taskId,
                    "PDF".equals(task.getTargetFormat()) ? "pdf" : "docx");
            var info = ossStorageAdapter.putBytes(objectKey, result.content(), result.contentType());
            String contentHash = sha256(result.content());
            OssObject object = new OssObject(Ids.hex16(), ossProperties.bucket(), ossProperties.region(),
                    objectKey, info.versionId(), info.etag(), contentHash, info.contentLength(),
                    result.contentType(), info.encryption() == null ? "NONE" : info.encryption(),
                    "CONVERTED");
            txWriter.succeeded(task, object, contentHash);
        } catch (ConversionEngineException e) {
            log.warn("转换失败 taskId={} code={} reason={}", taskId, e.getCode(), e.getMessage());
            // 1106 审查 P2-3：超时按计划落约定码 50401（failure_code 列语义与错误码字典一致）
            String code = "TIMEOUT".equals(e.getCode()) ? "50401" : e.getCode();
            txWriter.failed(taskId, code, e.getMessage(), task.getDocumentId());
        } catch (Exception e) {
            log.error("转换异常 taskId={}", taskId, e);
            txWriter.failed(taskId, "ENGINE_UNAVAILABLE", String.valueOf(e.getMessage()),
                    task.getDocumentId());
        }
    }

    private byte[] download(OssObject object) {
        Path temp = null;
        try {
            temp = Files.createTempFile("edudoc-convert-", ".bin");
            ossStorageAdapter.downloadTo(object.getObjectKey(), temp);
            return Files.readAllBytes(temp);
        } catch (java.io.IOException e) {
            throw new ConversionEngineException("ENGINE_UNAVAILABLE", "源文件下载失败: " + e.getMessage());
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

    private static String fileNameOf(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        return slash < 0 ? objectKey : objectKey.substring(slash + 1);
    }

    private static String monthDir() {
        return LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
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
