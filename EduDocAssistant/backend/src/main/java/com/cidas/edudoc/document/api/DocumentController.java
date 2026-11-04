package com.cidas.edudoc.document.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.document.api.dto.ParseRequest;
import com.cidas.edudoc.document.api.dto.ParseResponse;
import com.cidas.edudoc.document.application.DocumentLifecycleService;
import com.cidas.edudoc.document.application.DocumentPreviewService;
import com.cidas.edudoc.document.application.DocumentQueryService;
import com.cidas.edudoc.document.application.DocumentQueryService.Detail;
import com.cidas.edudoc.document.application.DocumentQueryService.ListQuery;
import com.cidas.edudoc.document.application.DocumentQueryService.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 文档查询与生命周期端点（详细设计 §3.1.3）。 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentQueryService documentQueryService;
    private final DocumentLifecycleService documentLifecycleService;
    private final DocumentPreviewService documentPreviewService;

    @GetMapping("/{documentId}/preview")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<DocumentPreviewService.Preview> preview(
            @PathVariable("documentId") String documentId,
            @RequestParam(name = "versionId", required = false) String versionId) {
        return ApiResponse.ok(documentPreviewService.preview(documentId, versionId));
    }

    @GetMapping
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<PageResult<Summary>> list(
            @RequestParam(name = "courseId", required = false) String courseId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(documentQueryService.list(
                new ListQuery(courseId, type, status, keyword, page, pageSize)));
    }

    @GetMapping("/{documentId}")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<Detail> detail(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentQueryService.detail(documentId));
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.DOCUMENT_DELETE)
    public void delete(@PathVariable("documentId") String documentId) {
        documentLifecycleService.delete(documentId);
    }

    @PostMapping("/{documentId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.DOCUMENT_DELETE)
    public void restore(@PathVariable("documentId") String documentId) {
        documentLifecycleService.restore(documentId);
    }

    @PostMapping("/{documentId}/parse")
    @RequirePermission(PermissionCode.DOCUMENT_EDIT)
    public ApiResponse<ParseResponse> reparse(@PathVariable("documentId") String documentId,
                                              @RequestBody(required = false) ParseRequest request) {
        String taskId = documentLifecycleService.reparse(documentId,
                request == null ? null : request.versionId());
        return ApiResponse.ok(new ParseResponse(taskId));
    }
}
