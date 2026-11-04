package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.application.AiGateway.EmbedOutcome;
import com.cidas.edudoc.ai.infrastructure.AiModelProfile;
import com.cidas.edudoc.ai.infrastructure.AiModelProfileRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.ChunkVector;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.PendingChunk;
import com.cidas.edudoc.ai.infrastructure.EmbedProperties;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 向量化 worker（C-03）：消费 `edudoc:stream:document-embed`，按版本取 PENDING 分块 →
 * 25/批调 AiGateway.embed → 维度校验（不符整批不落）→ 单事务 upsert 向量 + 块 READY + 进度回写。
 * 幂等：事件级任务行（idempotency_key=eventId）+ (chunk_id, profile) uk 覆盖写；重投不重复嵌入已成功批次。
 * 去重口径与解析 worker 一致：仅处理「versionId=当前版本」的事件。
 * ponytail: 无 DLQ/pending 回收，失败置 FAILED 由重新解析补发新事件重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbedWorker {

    static final String EMBED_PROFILE_CODE = "EMB_BGE_M3";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final EmbedProperties properties;
    private final DocumentRepository documentRepository;
    private final AiTaskRepository aiTaskRepository;
    private final AiModelProfileRepository profileRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final AiGateway aiGateway;
    private final EmbeddingTxService embeddingTxService;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean running;
    private ExecutorService pool;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || !started.compareAndSet(false, true)) {
            if (properties.enabled()) {
                log.info("向量化 worker 已在运行，跳过重复启动");
            } else {
                log.info("向量化 worker 已禁用（edudoc.embed.enabled=false）");
            }
            return;
        }
        ensureGroup();
        running = true;
        pool = Executors.newFixedThreadPool(properties.concurrency(), task -> {
            Thread thread = new Thread(task, "embed-worker");
            thread.setDaemon(true);
            return thread;
        });
        String instance = java.net.InetAddress.getLoopbackAddress().getHostName()
                + "-" + ProcessHandle.current().pid();
        for (int i = 0; i < properties.concurrency(); i++) {
            int index = i;
            pool.submit(() -> consumeLoop("embed-" + instance + "-" + index));
        }
        log.info("向量化 worker 已启动：stream={} group={} concurrency={}",
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
                    log.warn("embed stream 读取失败，2s 后重试：{}", e.getMessage());
                    sleep(2000);
                }
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (MapRecord<String, Object, Object> record : records) {
                String eventId = String.valueOf(record.getValue().getOrDefault("eventId", ""));
                String versionId = String.valueOf(record.getValue().getOrDefault("aggregateRef", ""));
                String documentId = "";
                try {
                    documentId = objectMapper.readTree(String.valueOf(record.getValue().get("payload")))
                            .path("documentId").asText();
                    processEvent(eventId, documentId, versionId);
                } catch (Exception e) {
                    log.error("向量化事件处理异常 eventId={} documentId={}", eventId, documentId, e);
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

    /** 可测主流程：事件去重 → CAS 领取 → 分批嵌入 → 维度校验 → 单批事务落库。 */
    public void processEvent(String eventId, String documentId, String versionId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null || !versionId.equalsIgnoreCase(doc.getCurrentVersionId())) {
            log.info("过期向量化事件跳过：documentId={} versionId={}", documentId, versionId);
            return;
        }
        AiTask task = aiTaskRepository.insertOrReuse(new AiTask(Ids.hex16(), documentId, versionId,
                "EMBED", "QUEUED", 5, null, "{\"source\":\"outbox\"}", doc.getOwnerId(), 0, eventId, null, 0));
        if (!"QUEUED".equals(task.getStatus())) {
            log.info("向量化任务不处于 QUEUED，跳过：taskId={} status={}", task.getId(), task.getStatus());
            return;
        }
        if (!aiTaskRepository.casStart(task.getId())) {
            log.info("向量化任务已被其他消费者领取：taskId={}", task.getId());
            return;
        }
        try {
            embedVersion(task.getId(), versionId);
        } catch (CidasBusinessException e) {
            log.warn("向量化失败 versionId={}：{}", versionId, e.getMessage());
            aiTaskRepository.markFailed(task.getId(), String.valueOf(e.errorCode().code()));
        } catch (Exception e) {
            log.error("向量化异常 versionId={}", versionId, e);
            aiTaskRepository.markFailed(task.getId(), "50001");
        }
    }

    private void embedVersion(String taskId, String versionId) {
        AiModelProfile profile = profileRepository.findByCode(EMBED_PROFILE_CODE)
                .filter(p -> "A".equals(p.getStatus()) && p.getEmbeddingDimension() != null)
                .orElseThrow(() -> new CidasBusinessException(
                        ErrorCode.SERVICE_UNAVAILABLE, "嵌入模型档案缺失或未启用"));
        List<PendingChunk> pending = embeddingRepository.selectPendingChunks(versionId);
        if (pending.isEmpty()) {
            aiTaskRepository.markSucceeded(taskId);
            return;
        }
        int batchSize = Math.max(1, properties.batchSize());
        int done = 0;
        for (int from = 0; from < pending.size(); from += batchSize) {
            List<PendingChunk> batch = pending.subList(from, Math.min(pending.size(), from + batchSize));
            List<String> texts = batch.stream().map(PendingChunk::contentText).toList();
            EmbedOutcome outcome = aiGateway.embed(texts);
            validateDimensions(outcome, profile.getEmbeddingDimension());
            List<ChunkVector> vectors = new ArrayList<>();
            for (int i = 0; i < batch.size(); i++) {
                vectors.add(new ChunkVector(batch.get(i).chunkId(), toVectorLiteral(outcome.vectors().get(i))));
            }
            done += batch.size();
            int progress = (int) Math.floor(done * 95.0 / pending.size());
            embeddingTxService.storeBatch(taskId, profile.getId(), profile.getEmbeddingDimension(),
                    vectors, progress);
        }
        aiTaskRepository.markSucceeded(taskId);
        log.info("向量化完成 versionId={} chunks={}", versionId, pending.size());
    }

    private static void validateDimensions(EmbedOutcome outcome, int expected) {
        for (float[] vector : outcome.vectors()) {
            if (vector.length != expected) {
                throw new CidasBusinessException(
                        ErrorCode.MODEL_FAILED,
                        "向量维度不符：期望 " + expected + "，实际 " + vector.length,
                        true, Map.of("stage", "embed"));
            }
        }
    }

    /** 向量 → TO_VECTOR 可解析的 `[a,b,…]` 字符串（Float.toString 精确往返）。 */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
