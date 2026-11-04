package com.cidas.edudoc.ai.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.ai.application.AiTaskOrchestrator;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 生成任务创建端点（表 3-4：summaries/keypoints）。202 受理，进度走 GET /ai-tasks/{taskId}。 */
@RestController
@RequiredArgsConstructor
public class AiGenerationController {

    private final AiTaskOrchestrator orchestrator;

    public record TaskCreated(String taskId, String status) {
    }

    @PostMapping("/api/v1/documents/{documentId}/summaries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(PermissionCode.AI_SUMMARY)
    public ApiResponse<TaskCreated> createSummary(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(new TaskCreated(orchestrator.createTask(documentId, "SUMMARY"), "QUEUED"));
    }

    @PostMapping("/api/v1/documents/{documentId}/keypoints")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(PermissionCode.AI_KEYPOINT)
    public ApiResponse<TaskCreated> createKeypoint(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(new TaskCreated(orchestrator.createTask(documentId, "KEYPOINT"), "QUEUED"));
    }
}
