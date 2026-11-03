package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.Chunk;
import com.cidas.edudoc.document.domain.ChunkingService;
import com.cidas.edudoc.document.domain.TextBlock;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.ParseProperties;
import com.cidas.edudoc.document.infrastructure.TikaTextExtractor;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
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
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 解析 worker（B-06）：消费 `edudoc:stream:document-parse`，Tika 提取 → 分块 → 单事务落库。
 * 去重以文档状态为最终事实（概要 §2.4）：仅处理「文档 PARSING 且事件版本=当前版本」的事件。
 * ponytail: 无 DLQ/pending 回收，失败置 FAILED 由「重新解析」重试，需要时加 XAUTOCLAIM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseWorker {

    private final StringRedisTemplate redis;
    private final OssStorageAdapter ossStorageAdapter;
    private final TikaTextExtractor extractor;
    private final DocumentRepository documentRepository;
    private final DocumentParseTxService parseTxService;
    private final ParseProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean running;
    private ExecutorService pool;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || !started.compareAndSet(false, true)) {
            if (properties.enabled()) {
                log.info("解析 worker 已在运行，跳过重复启动");
            } else {
                log.info("解析 worker 已禁用（edudoc.parse.enabled=false）");
            }
            return;
        }
        ensureGroup();
        running = true;
        pool = Executors.newFixedThreadPool(properties.concurrency(), task -> {
            Thread thread = new Thread(task, "parse-worker");
            thread.setDaemon(true);
            return thread;
        });
        String host = hostName();
        for (int i = 0; i < properties.concurrency(); i++) {
            int index = i;
            pool.submit(() -> consumeLoop("parse-" + host + "-" + index));
        }
        log.info("解析 worker 已启动：stream={} group={} concurrency={}",
                properties.streamKey(), properties.group(), properties.concurrency());
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    /** XGROUP CREATE ... MKSTREAM 幂等：组已存在（BUSYGROUP）视为成功——注意异常被 Spring 包装，需沿 cause 链找。 */
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
        StreamOperations<String, Object, Object> stream = redis.opsForStream();
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
                    log.warn("Stream 读取失败，2s 后重试：{}", e.getMessage());
                    sleep(2000);
                }
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (MapRecord<String, Object, Object> record : records) {
                handle(stream, consumer, record);
            }
        }
    }

    void handle(StreamOperations<String, Object, Object> stream, Consumer consumer,
                MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        String payload = String.valueOf(value.get("payload"));
        String versionId = String.valueOf(value.getOrDefault("aggregateRef", ""));
        String documentId = "";
        try {
            documentId = objectMapper.readTree(payload).path("documentId").asText();
            String objectKey = objectMapper.readTree(payload).path("objectKey").asText();
            var doc = documentRepository.findById(documentId).orElse(null);
            if (doc == null) {
                log.warn("解析事件对应文档不存在，丢弃：documentId={}", documentId);
            } else if (!"PARSING".equals(doc.getStatus()) || !versionId.equalsIgnoreCase(doc.getCurrentVersionId())) {
                log.info("过期解析事件跳过：documentId={} status={}", documentId, doc.getStatus());
            } else {
                process(documentId, versionId, objectKey);
            }
        } catch (Exception e) {
            log.error("解析失败 documentId={}", documentId, e);
            if (!documentId.isEmpty()) {
                try {
                    parseTxService.writeFailure(documentId, versionId, e.getMessage());
                } catch (Exception inner) {
                    log.error("解析失败状态回写失败 documentId={}", documentId, inner);
                }
            }
        } finally {
            try {
                stream.acknowledge(properties.streamKey(), properties.group(), record.getId());
            } catch (Exception e) {
                log.warn("XACK 失败 record={}", record.getId(), e);
            }
        }
    }

    private void process(String documentId, String versionId, String objectKey) throws Exception {
        Path temp = Files.createTempFile("edudoc-parse-", ".bin");
        try {
            ossStorageAdapter.downloadTo(objectKey, temp);
            List<TextBlock> blocks = extractor.extract(temp, fileNameOf(objectKey));
            List<Chunk> chunks = new ChunkingService(properties.chunkSize()).chunk(blocks);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("文档无可提取文本（空文档或扫描件）");
            }
            parseTxService.writeSuccess(documentId, versionId, chunks);
            log.info("解析成功 documentId={} versionId={} chunks={}", documentId, versionId, chunks.size());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String fileNameOf(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        return slash < 0 ? objectKey : objectKey.substring(slash + 1);
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
