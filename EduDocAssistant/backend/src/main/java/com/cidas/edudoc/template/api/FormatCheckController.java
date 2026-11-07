package com.cidas.edudoc.template.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.template.application.FormatCheckService;
import com.cidas.edudoc.template.application.FormatCheckService.CheckView;
import com.cidas.edudoc.template.application.FormatCheckService.IssueView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 格式校验端点（详细设计表 3-6，D-04）。 */
@RestController
@RequiredArgsConstructor
public class FormatCheckController {

    private final FormatCheckService formatCheckService;

    @PostMapping("/api/v1/documents/{documentId}/format-checks")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.FORMAT_CHECK)
    public ApiResponse<CheckView> create(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(formatCheckService.create(documentId));
    }

    @GetMapping("/api/v1/format-checks/{checkId}")
    @RequirePermission(PermissionCode.FORMAT_CHECK)
    public ApiResponse<CheckView> get(@PathVariable("checkId") String checkId) {
        return ApiResponse.ok(formatCheckService.get(checkId));
    }

    @GetMapping("/api/v1/format-checks/{checkId}/issues")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<PageResult<IssueView>> issues(@PathVariable("checkId") String checkId,
                                                     @RequestParam(name = "severity", required = false)
                                                     String severity,
                                                     @RequestParam(name = "page", required = false) Integer page,
                                                     @RequestParam(name = "pageSize", required = false)
                                                     Integer pageSize) {
        return ApiResponse.ok(formatCheckService.issues(checkId, severity, page, pageSize));
    }
}
