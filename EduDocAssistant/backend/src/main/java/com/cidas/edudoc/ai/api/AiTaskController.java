package com.cidas.edudoc.ai.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.ai.application.AiResultExportService;
import com.cidas.edudoc.ai.application.AiTaskOrchestrator;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** 任务查询/取消与结果导出（表 3-4 + C-04 导出最小实现）。 */
@RestController
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskOrchestrator orchestrator;
    private final AiResultExportService exportService;

    @GetMapping("/api/v1/ai-tasks/{taskId}")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<AiTaskOrchestrator.TaskView> getTask(@PathVariable("taskId") String taskId) {
        return ApiResponse.ok(orchestrator.getTask(taskId));
    }

    @PostMapping("/api/v1/ai-tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public void cancel(@PathVariable("taskId") String taskId) {
        orchestrator.cancel(taskId);
    }

    @GetMapping("/api/v1/ai-results/{resultId}/export")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ResponseEntity<ByteArrayResource> export(@PathVariable("resultId") String resultId,
            @RequestParam(name = "format", defaultValue = "docx") String format) {
        AiResultExportService.ExportFile file = exportService.export(resultId, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(new ByteArrayResource(file.content()));
    }
}
