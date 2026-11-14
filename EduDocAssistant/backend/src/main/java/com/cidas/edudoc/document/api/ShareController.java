package com.cidas.edudoc.document.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.document.application.ShareService;
import com.cidas.edudoc.document.application.ShareService.CreateCommand;
import com.cidas.edudoc.document.application.ShareService.CreatedShare;
import com.cidas.edudoc.document.application.ShareService.PublicShare;
import com.cidas.edudoc.document.application.ShareService.ShareView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档共享链接端点：owner 创建/列出/撤销；{@code /public/shares/{token}} 免登录只读。
 * 公开路径在 SecurityConfig.permitAll，响应只含标题与当前版本文本，不含 OSS 对象信息。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/documents/{documentId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_SHARE)
    public ApiResponse<CreatedShare> create(@PathVariable("documentId") String documentId,
                                            @RequestBody CreateShareRequest request) {
        return ApiResponse.ok(shareService.create(documentId,
                new CreateCommand(request.expiresInDays(), request.permission())));
    }

    @GetMapping("/documents/{documentId}/shares")
    @RequirePermission(PermissionCode.DOCUMENT_SHARE)
    public ApiResponse<List<ShareView>> list(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(shareService.list(documentId));
    }

    @DeleteMapping("/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.DOCUMENT_SHARE)
    public void revoke(@PathVariable("shareId") String shareId) {
        shareService.revoke(shareId);
    }

    /** 免登录只读：无 token 即无主体，权限点体系不参与（SecurityConfig permitAll）。 */
    @GetMapping("/public/shares/{token}")
    public ApiResponse<PublicShare> publicView(@PathVariable("token") String token) {
        return ApiResponse.ok(shareService.publicView(token));
    }

    public record CreateShareRequest(Integer expiresInDays, String permission) {
    }
}