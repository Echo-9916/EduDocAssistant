package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.ai.application.AiTaskOrchestrator;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 任务编排实现（C-04）。幂等口径见风险表 R1：HTTP 层 IdempotencyFilter 同键 40902 不改，
 * 业务幂等 = 同 (版本, 类型, 发起人) 存在 QUEUED/RUNNING 任务直接复用其 taskId。
 */
@Service
@RequiredArgsConstructor
public class AiTaskOrchestratorImpl implements AiTaskOrchestrator {

    private final DocumentRepository documentRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final AiTaskRepository aiTaskRepository;
    private final AiResultRepository aiResultRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public String createTask(String documentId, String taskType) {
        UserPrincipal principal = AuthContext.require();
        // 1105 审查 P2-3：DOCUMENT_READ 与 AI_* 权限点组合校验（注解之外服务层兜底）
        assertPermission(principal, PermissionCode.DOCUMENT_READ);
        assertPermission(principal, permissionOf(taskType));
        Document doc = requireVisible(documentId);
        if (!"READY".equals(doc.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档尚未就绪");
        }
        String versionId = doc.getCurrentVersionId();
        if (embeddingRepository.countNotReady(versionId) > 0
                || embeddingRepository.selectVersionChunks(versionId).isEmpty()) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档尚未就绪（向量索引未完成）");
        }
        return aiTaskRepository.findActive(versionId, taskType, principal.userId())
                .map(AiTask::getId)
                .orElseGet(() -> newTask(doc, versionId, taskType, principal));
    }

    private String newTask(Document doc, String versionId, String taskType, UserPrincipal principal) {
        String taskId = Ids.hex16();
        aiTaskRepository.insertOrReuse(new AiTask(taskId, doc.getId(), versionId, taskType,
                "QUEUED", 5, null, jsonPayload(doc.getId(), versionId, taskType),
                principal.userId(), 0, null, null, 0));
        outboxEventRepository.publish("AI_TASK", taskId, "AI_TASK_REQUESTED", Map.of(
                "schemaVersion", 1, "taskId", taskId, "taskType", taskType,
                "documentId", doc.getId(), "versionId", versionId));
        return taskId;
    }

    @Override
    public TaskView getTask(String taskId) {
        AiTask task = requireOwnTask(taskId);
        ResultView result = "SUCCEEDED".equals(task.getStatus()) ? loadResult(taskId) : null;
        return new TaskView(task.getId(), task.getDocumentId(), task.getTaskType(), task.getStatus(),
                task.getProgress(), task.getErrorCode(), result);
    }

    @Override
    public void cancel(String taskId) {
        String status = requireOwnTask(taskId).getStatus();
        // 取消标记 = 直接置 CANCELED：worker 结果写回以 status=RUNNING 为守卫（竞争兜底，表无 cancel_flag 列）
        if ("QUEUED".equals(status) || "RUNNING".equals(status)) {
            if (aiTaskRepository.cancelQueued(taskId) || aiTaskRepository.cancelRunning(taskId)) {
                return;
            }
            throw new CidasBusinessException(ErrorCode.CONFLICT, "任务状态已变化，请重试");
        }
        throw new CidasBusinessException(ErrorCode.CONFLICT, "任务已结束，不可取消");
    }

    private ResultView loadResult(String taskId) {
        return aiResultRepository.findLatestByTask(taskId).map(this::toView).orElse(null);
    }

    private ResultView toView(AiResult result) {
        List<CitationView> citations = aiResultRepository.findCitations(result.getId()).stream()
                .map(c -> new CitationView(c.getId(), c.getChunkId(), c.getPageNo(),
                        c.getCharStart(), c.getCharEnd(), c.getQuoteText(), c.getRankNo()))
                .toList();
        return new ResultView(result.getId(), result.getResultType(), result.getContentText(),
                result.getStructuredJson(), result.getModelName(), result.getPromptVersion(),
                result.getInputTokens(), result.getOutputTokens(), citations);
    }

    private AiTask requireOwnTask(String taskId) {
        if (!Ids.isHex16(taskId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        AiTask task = aiTaskRepository.findById(taskId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!task.getRequestedBy().equalsIgnoreCase(AuthContext.require().userId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "任务不存在或不可见");
        }
        return task;
    }

    private Document requireVisible(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND);
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!AuthContext.require().userId().equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在或不可见");
        }
        return doc;
    }

    private static void assertPermission(UserPrincipal principal, String permission) {
        if (!principal.permissions().contains(permission)) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static String permissionOf(String taskType) {
        return switch (taskType) {
            case "SUMMARY" -> PermissionCode.AI_SUMMARY;
            case "KEYPOINT" -> PermissionCode.AI_KEYPOINT;
            default -> throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "不支持的任务类型：" + taskType);
        };
    }

    private String jsonPayload(String documentId, String versionId, String taskType) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("documentId", documentId, "versionId", versionId, "taskType", taskType));
        } catch (Exception e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "任务负载序列化失败");
        }
    }
}
