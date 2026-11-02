package com.cidas.edudoc.document.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.document.api.dto.CompleteUploadRequest;
import com.cidas.edudoc.document.api.dto.CompleteUploadResponse;
import com.cidas.edudoc.document.api.dto.CreateUploadSessionRequest;
import com.cidas.edudoc.document.api.dto.CreateUploadSessionResponse;
import com.cidas.edudoc.document.application.UploadSessionService;
import com.cidas.edudoc.document.application.UploadSessionService.CompleteCommand;
import com.cidas.edudoc.document.application.UploadSessionService.CompletionResult;
import com.cidas.edudoc.document.application.UploadSessionService.NewUpload;
import com.cidas.edudoc.document.application.UploadSessionService.SessionTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 上传会话端点（详细设计 §3.1.3）；写接口由 IdempotencyFilter 强制 Idempotency-Key。 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class UploadController {

    private final UploadSessionService uploadSessionService;

    @PostMapping("/upload-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_CREATE)
    public ApiResponse<CreateUploadSessionResponse> createSession(
            @RequestBody CreateUploadSessionRequest request) {
        SessionTicket ticket = uploadSessionService.createSession(new NewUpload(
                request.courseId(), request.title(), request.fileName(), request.size(),
                request.mimeType(), request.docType()));
        return ApiResponse.ok(CreateUploadSessionResponse.from(ticket));
    }

    @PostMapping("/upload-sessions/{sessionId}/complete")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_CREATE)
    public ApiResponse<CompleteUploadResponse> complete(
            @PathVariable("sessionId") String sessionId,
            @RequestBody CompleteUploadRequest request) {
        CompletionResult result = uploadSessionService.completeUpload(sessionId,
                new CompleteCommand(request.etag(), request.contentHash()));
        return ApiResponse.ok(CompleteUploadResponse.from(result));
    }
}
