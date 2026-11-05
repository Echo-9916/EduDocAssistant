package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.ai.application.AiGateway.SourceChunk;
import com.cidas.edudoc.ai.application.AiGateway.SummarizeOutcome;
import com.cidas.edudoc.ai.application.AiGateway.SummarySegment;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.AiTaskWorkerProperties;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
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
 * AI 生成任务 worker（C-04）：消费 `edudoc:stream:ai-task`，CAS 置 RUNNING（重复投递按状态去重，UT-20），
 * 摘要走分段 map-reduce（分组 ≤groupChars、逐组进度回写），结果 + 引用 + 审计在守卫事务内一次落定；
 * 取消竞争：进度/结果写回均以 status=RUNNING 为守卫，落败即静默丢弃（表无 cancel_flag 列，C-03 台账裁决）。
 * 单任务预算 timeoutSeconds，超预算 FAILED(50401)。
 * ponytail: 无 DLQ/pending 回收；FAILED 由用户重新发起任务重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskWorker {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiTaskWorkerProperties properties;
    private final AiTaskRepository aiTaskRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final AiGateway aiGateway;
    private final CitationService citationService;
    private final AiResultTxService resultTxService;
    private final AuditService auditService;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean running;
    private ExecutorService pool;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.enabled() || !started.compareAndSet(false, true)) {
            if (properties.enabled()) {
                log.info("AI 任务 worker 已在运行，跳过重复启动");
            } else {
                log.info("AI 任务 worker 已禁用（edudoc.ai-task.enabled=false）");
            }
            return;
        }
        ensureGroup();
        running = true;
        pool = Executors.newFixedThreadPool(properties.concurrency(), task -> {
            Thread thread = new Thread(task, "ai-task-worker");
            thread.setDaemon(true);
            return thread;
        });
        String instance = java.net.InetAddress.getLoopbackAddress().getHostName()
                + "-" + ProcessHandle.current().pid();
        for (int i = 0; i < properties.concurrency(); i++) {
            int index = i;
            pool.submit(() -> consumeLoop("ai-" + instance + "-" + index));
        }
        log.info("AI 任务 worker 已启动：stream={} group={} concurrency={}",
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
                    log.warn("ai-task stream 读取失败，2s 后重试：{}", e.getMessage());
                    sleep(2000);
                }
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (MapRecord<String, Object, Object> record : records) {
                String taskId = "";
                try {
                    taskId = objectMapper.readTree(String.valueOf(record.getValue().get("payload")))
                            .path("taskId").asText();
                    processTask(taskId);
                } catch (Exception e) {
                    log.error("AI 任务处理异常 taskId={}", taskId, e);
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

    public void processTask(String taskId) {
        AiTask task = aiTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("AI 任务不存在，丢弃：taskId={}", taskId);
            return;
        }
        if (!"QUEUED".equals(task.getStatus())) {
            log.info("AI 任务重复投递跳过：taskId={} status={}", taskId, task.getStatus());
            return;
        }
        if (!aiTaskRepository.casStart(taskId)) {
            log.info("AI 任务已被其他消费者领取：taskId={}", taskId);
            return;
        }
        long beganAt = System.nanoTime();
        try {
            switch (task.getTaskType()) {
                case "SUMMARY" -> handleSummary(task, beganAt);
                case "KEYPOINT" -> handleKeypoint(task, beganAt);
                default -> aiTaskRepository.markFailed(taskId, "50001");
            }
        } catch (CidasBusinessException e) {
            log.warn("AI 任务失败 taskId={}：{}", taskId, e.getMessage());
            aiTaskRepository.markFailed(taskId, String.valueOf(e.errorCode().code()));
            auditFailed(task, e);
        } catch (Exception e) {
            log.error("AI 任务异常 taskId={}", taskId, e);
            aiTaskRepository.markFailed(taskId, "50001");
            auditFailed(task, new CidasBusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage()));
        } finally {
            // 终态（含失败）释放 ACTIVE 锁键（P2-5）；已被取消路径释放过则 0 行无害
            aiTaskRepository.releaseActiveKey(taskId);
        }
    }

    private void handleSummary(AiTask task, long beganAt) {
        List<VersionChunk> chunks = embeddingRepository.selectVersionChunks(task.getDocumentVersionId());
        if (chunks.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.MODEL_FAILED, "版本无分块，无法摘要");
        }
        List<List<VersionChunk>> groups = groupChunks(chunks, properties.groupChars());
        List<SummarySegment> segments = new ArrayList<>();
        String model = null;
        String promptVersion = null;
        int inputTokens = 0;
        int outputTokens = 0;
        for (int i = 0; i < groups.size(); i++) {
            checkBudget(beganAt);
            SummarizeOutcome outcome = aiGateway.summarize(groups.get(i).stream()
                    .map(c -> new SourceChunk(c.chunkId(), c.contentText())).toList());
            segments.addAll(outcome.segments());
            if (outcome.model() != null) {
                model = outcome.model();
            }
            if (outcome.promptVersion() != null) {
                promptVersion = outcome.promptVersion();
            }
            if (outcome.usage() != null) {
                inputTokens += outcome.usage().inputTokens() == null ? 0 : outcome.usage().inputTokens();
                outputTokens += outcome.usage().outputTokens() == null ? 0 : outcome.usage().outputTokens();
            }
            int progress = 5 + (int) ((i + 1) * 90.0 / groups.size());
            if (!aiTaskRepository.updateProgress(task.getId(), Math.min(progress, 99))) {
                log.info("摘要任务已被取消，静默丢弃进度后续：taskId={}", task.getId());
                return;
            }
        }
        checkBudget(beganAt);

        // 真 map-reduce（1105 审查 P3-2）：多组时把组摘要段再做一次汇总结论
        if (groups.size() > 1 && !segments.isEmpty()) {
            List<SourceChunk> segRefs = new ArrayList<>();
            for (SummarySegment s : segments) {
                segRefs.add(new SourceChunk(s.chunkIds().isEmpty() ? "" : s.chunkIds().get(0), s.text()));
            }
            List<SummarySegment> reduced = aiGateway.summarizeReduce(segRefs).segments();
            if (!reduced.isEmpty()) {
                segments = reduced;
            }
        }

        Map<String, VersionChunk> validChunks = citationService.indexByVersion(chunks);
        List<AiCitation> citations = new ArrayList<>();
        List<Map<String, Object>> segmentViews = new ArrayList<>();
        int rank = 1;
        for (SummarySegment segment : segments) {
            for (String chunkId : segment.chunkIds()) {
                VersionChunk chunk = chunkId == null ? null : validChunks.get(chunkId);
                if (chunk == null) {
                    continue; // 非法引用丢弃（chunkId 不在本版本分块表即非法，模型不可造块）
                }
                citations.add(new AiCitation(Ids.hex16(), "PENDING", chunk.chunkId(), chunk.pageNo(),
                        chunk.charStart(), chunk.charEnd(),
                        AiResultRepository.clipQuote(chunk.contentText()), rank++));
            }
            segmentViews.add(Map.of("text", segment.text(),
                    "chunkIds", segment.chunkIds().stream().filter(validChunks::containsKey).toList()));
        }
        String resultId = Ids.hex16();
        citations.forEach(c -> c.setResultId(resultId));
        String structured = writeJson(Map.of("segments", segmentViews, "groupCount", groups.size()));
        AiResult result = new AiResult(resultId, task.getId(), "SUMMARY",
                String.join("\n", segments.stream().map(SummarySegment::text).toList()),
                structured, model == null ? "unknown" : model,
                promptVersion == null ? "unknown" : promptVersion,
                inputTokens == 0 ? null : inputTokens, outputTokens == 0 ? null : outputTokens);
        if (!resultTxService.completeTask(result, citations)) {
            log.info("摘要任务写回竞争落败（已取消），丢弃结果：taskId={}", task.getId());
            return;
        }
        auditService.record("AI_TASK_SUCCEEDED", "AI_TASK", task.getId(), task.getDocumentId(), "200",
                Map.of("taskType", "SUMMARY", "model", model == null ? "" : model,
                        "promptVersion", promptVersion == null ? "" : promptVersion,
                        "elapsedMs", (System.nanoTime() - beganAt) / 1_000_000,
                        "inputTokens", inputTokens, "outputTokens", outputTokens));
    }

    /** C-05 要点：分组提取 → 非法 chunkId 丢弃 → 按引用块 char_start 重排为文档顺序 → <5 条标记 underMin。 */
    private void handleKeypoint(AiTask task, long beganAt) {
        List<VersionChunk> chunks = embeddingRepository.selectVersionChunks(task.getDocumentVersionId());
        if (chunks.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.MODEL_FAILED, "版本无分块，无法提取要点");
        }
        Map<String, VersionChunk> validChunks = citationService.indexByVersion(chunks);
        List<List<VersionChunk>> groups = groupChunks(chunks, properties.groupChars());
        List<AiGateway.Keypoint> raw = new ArrayList<>();
        String model = null;
        String promptVersion = null;
        int inputTokens = 0;
        int outputTokens = 0;
        for (int i = 0; i < groups.size(); i++) {
            checkBudget(beganAt);
            AiGateway.KeypointOutcome outcome = aiGateway.extractKeypoints(groups.get(i).stream()
                    .map(c -> new SourceChunk(c.chunkId(), c.contentText())).toList());
            raw.addAll(outcome.keypoints());
            if (outcome.model() != null) {
                model = outcome.model();
            }
            if (outcome.promptVersion() != null) {
                promptVersion = outcome.promptVersion();
            }
            if (outcome.usage() != null) {
                inputTokens += outcome.usage().inputTokens() == null ? 0 : outcome.usage().inputTokens();
                outputTokens += outcome.usage().outputTokens() == null ? 0 : outcome.usage().outputTokens();
            }
            int progress = 5 + (int) ((i + 1) * 90.0 / groups.size());
            if (!aiTaskRepository.updateProgress(task.getId(), Math.min(progress, 99))) {
                log.info("要点任务已被取消，静默丢弃：taskId={}", task.getId());
                return;
            }
        }
        checkBudget(beganAt);

        record OrderedKeypoint(VersionChunk chunk, AiGateway.Keypoint keypoint) {
        }
        List<OrderedKeypoint> kept = raw.stream()
                .filter(k -> k.chunkId() != null && validChunks.containsKey(k.chunkId()))
                .map(k -> new OrderedKeypoint(validChunks.get(k.chunkId()), k))
                .sorted(java.util.Comparator
                        .comparingInt((OrderedKeypoint o) -> o.chunk().charStart() == null
                                ? Integer.MAX_VALUE : o.chunk().charStart())
                        .thenComparingInt(o -> o.chunk().chunkNo()))
                .toList();
        boolean underMin = kept.size() < 5; // 不判失败，UI 提示（REQ-11 口径）
        List<AiCitation> citations = new ArrayList<>();
        List<Map<String, Object>> views = new ArrayList<>();
        String resultId = Ids.hex16();
        int rank = 1;
        StringBuilder content = new StringBuilder();
        for (OrderedKeypoint o : kept) {
            VersionChunk chunk = o.chunk();
            citations.add(new AiCitation(Ids.hex16(), resultId, chunk.chunkId(), chunk.pageNo(),
                    chunk.charStart(), chunk.charEnd(),
                    AiResultRepository.clipQuote(chunk.contentText()), rank));
            content.append(rank).append(". ").append(o.keypoint().text()).append('\n');
            views.add(Map.of("text", o.keypoint().text(), "chunkId", chunk.chunkId(),
                    "charStart", chunk.charStart() == null ? -1 : chunk.charStart()));
            rank++;
        }
        Map<String, Object> structuredMap = new java.util.LinkedHashMap<>();
        structuredMap.put("keypoints", views);
        structuredMap.put("underMin", underMin);
        String structured = writeJson(structuredMap);
        AiResult result = new AiResult(resultId, task.getId(), "KEYPOINT", content.toString(),
                structured, model == null ? "unknown" : model,
                promptVersion == null ? "unknown" : promptVersion,
                inputTokens == 0 ? null : inputTokens, outputTokens == 0 ? null : outputTokens);
        if (!resultTxService.completeTask(result, citations)) {
            log.info("要点任务写回竞争落败（已取消），丢弃结果：taskId={}", task.getId());
            return;
        }
        auditService.record("AI_TASK_SUCCEEDED", "AI_TASK", task.getId(), task.getDocumentId(), "200",
                Map.of("taskType", "KEYPOINT", "model", model == null ? "" : model,
                        "promptVersion", promptVersion == null ? "" : promptVersion,
                        "elapsedMs", (System.nanoTime() - beganAt) / 1_000_000,
                        "keypointCount", kept.size(), "underMin", underMin));
    }

    /** 按 chunk 序累计字符数 ≤groupChars 分组；单块超限自成一组（chunk_size 1500 下不发生）。 */
    static List<List<VersionChunk>> groupChunks(List<VersionChunk> chunks, int groupChars) {
        List<List<VersionChunk>> groups = new ArrayList<>();
        List<VersionChunk> current = new ArrayList<>();
        int size = 0;
        for (VersionChunk chunk : chunks) {
            int len = chunk.contentText() == null ? 0 : chunk.contentText().length();
            if (!current.isEmpty() && size + len + 1 > groupChars) {
                groups.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(chunk);
            size += size == 0 ? len : len + 1;
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private void checkBudget(long beganAtNanos) {
        long elapsedSeconds = (System.nanoTime() - beganAtNanos) / 1_000_000_000;
        if (elapsedSeconds >= properties.timeoutSeconds()) {
            throw new CidasBusinessException(ErrorCode.TIMEOUT,
                    "任务超预算 " + properties.timeoutSeconds() + "s", true, Map.of());
        }
    }

    private void auditFailed(AiTask task, CidasBusinessException e) {
        try {
            auditService.record("AI_TASK_FAILED", "AI_TASK", task.getId(), task.getDocumentId(),
                    String.valueOf(e.errorCode().code()), Map.of("taskType", task.getTaskType()));
        } catch (Exception inner) {
            log.warn("AI 任务失败审计写入异常：{}", inner.getMessage());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "结果结构化序列化失败");
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
