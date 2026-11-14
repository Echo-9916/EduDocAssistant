package com.cidas.edudoc.document.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.document.application.AnnotationService;
import com.cidas.edudoc.document.application.AnnotationService.AnnotationView;
import com.cidas.edudoc.document.application.AnnotationService.CreateCommand;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.application.CollaborationService.AddCollaboratorCommand;
import com.cidas.edudoc.document.application.CollaborationService.CollaboratorView;
import com.cidas.edudoc.document.application.CollaborationService.PresenceView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 批注、协作者与在线状态端点。权限点只做登录后入口校验，owner/协作者判定在应用服务手写；
 * 公开共享链接没有用户主体，天然不经过这些端点。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CollaborationController {

    private final AnnotationService annotationService;
    private final CollaborationService collaborationService;

    // ---------- 批注 ----------

    @GetMapping("/documents/{documentId}/annotations")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<List<AnnotationView>> listAnnotations(
            @PathVariable("documentId") String documentId,
            @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.ok(annotationService.list(documentId, status));
    }

    @PostMapping("/documents/{documentId}/annotations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<AnnotationView> createAnnotation(@PathVariable("documentId") String documentId,
                                                        @RequestBody CreateAnnotationRequest request) {
        return ApiResponse.ok(annotationService.create(documentId,
                new CreateCommand(request.versionId(), request.charStart(), request.charEnd(), request.content())));
    }

    @PatchMapping("/annotations/{annotationId}/resolve")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<AnnotationView> resolve(@PathVariable("annotationId") String annotationId,
                                               @RequestBody(required = false) ResolveRequest request) {
        boolean resolved = request == null || request.resolved() == null || request.resolved();
        return ApiResponse.ok(annotationService.resolve(annotationId, resolved));
    }

    @DeleteMapping("/annotations/{annotationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public void delete(@PathVariable("annotationId") String annotationId) {
        annotationService.delete(annotationId);
    }

    // ---------- 协作者 ----------

    @GetMapping("/documents/{documentId}/collaborators")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<List<CollaboratorView>> collaborators(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(collaborationService.listCollaborators(documentId));
    }

    @PostMapping("/documents/{documentId}/collaborators")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<CollaboratorView> addCollaborator(@PathVariable("documentId") String documentId,
                                                         @RequestBody AddCollaboratorRequest request) {
        return ApiResponse.ok(collaborationService.addCollaborator(documentId,
                new AddCollaboratorCommand(request.username(), request.permission())));
    }

    @DeleteMapping("/documents/{documentId}/collaborators/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public void removeCollaborator(@PathVariable("documentId") String documentId,
                                   @PathVariable("userId") String userId) {
        collaborationService.removeCollaborator(documentId, userId);
    }

    // ---------- 在线状态 ----------

    @PostMapping("/documents/{documentId}/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public void heartbeat(@PathVariable("documentId") String documentId) {
        collaborationService.heartbeat(documentId);
    }

    @GetMapping("/documents/{documentId}/presence")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<List<PresenceView>> presence(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(collaborationService.presence(documentId));
    }

    public record CreateAnnotationRequest(String versionId, Integer charStart, Integer charEnd, String content) {
    }

    public record ResolveRequest(Boolean resolved) {
    }

    public record AddCollaboratorRequest(String username, String permission) {
    }
}