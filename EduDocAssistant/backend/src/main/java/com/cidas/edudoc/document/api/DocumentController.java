package com.cidas.edudoc.document.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.document.api.dto.ParseRequest;
import com.cidas.edudoc.document.api.dto.ParseResponse;
import com.cidas.edudoc.document.application.DocumentBlockEditService;
import com.cidas.edudoc.document.application.DocumentBlockEditService.Block;
import com.cidas.edudoc.document.application.DocumentBlockEditService.Result;
import com.cidas.edudoc.document.application.DocumentImageService;
import com.cidas.edudoc.document.application.DocumentImageService.ImageView;
import com.cidas.edudoc.document.application.DocumentImageService.Upload;
import com.cidas.edudoc.document.application.DocumentLifecycleService;
import com.cidas.edudoc.document.application.DocumentPreviewService;
import com.cidas.edudoc.document.application.DocumentQueryService;
import com.cidas.edudoc.document.application.DocumentWordExportService;
import com.cidas.edudoc.document.application.DocumentQueryService.Detail;
import com.cidas.edudoc.document.application.DocumentQueryService.ListQuery;
import com.cidas.edudoc.document.application.DocumentQueryService.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 文档查询与生命周期端点（详细设计 §3.1.3）。 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentQueryService documentQueryService;
    private final DocumentLifecycleService documentLifecycleService;
    private final DocumentPreviewService documentPreviewService;
    private final DocumentBlockEditService documentBlockEditService;
    private final DocumentImageService documentImageService;
    private final DocumentWordExportService documentWordExportService;

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

    /** 验收决议④：版本历史（owner 可见；版本号降序 + 每版本源文件短期签名下载）。 */
    @GetMapping("/{documentId}/versions")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<java.util.List<com.cidas.edudoc.document.application.DocumentQueryService.VersionView>>
            versions(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentQueryService.listVersions(documentId));
    }

    /** 恢复历史版本：复制为新版本并前移当前版本（写审计；non-owner 40401，无编辑权 40301）。 */
    @PostMapping("/{documentId}/versions/{versionId}/restore")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_EDIT)
    public ApiResponse<DocumentLifecycleService.RestoredVersion> restoreVersion(
            @PathVariable("documentId") String documentId,
            @PathVariable("versionId") String versionId) {
        return ApiResponse.ok(documentLifecycleService.restoreVersion(documentId, versionId));
    }

    /** 格式视图字节流：某版本源文件（前端 fetch→blob 内嵌；owner 校验同 detail）。 */
    /** 块编辑：H1/H2/正文/代码块整篇保存为新 EDIT 版本（仅 owner）。 */
    @PostMapping("/{documentId}/block-edits")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_EDIT)
    public ApiResponse<Result> blockEdit(@PathVariable("documentId") String documentId,
                                         @RequestBody BlockEditRequest request) {
        var blocks = request.blocks() == null ? java.util.List.<Block>of()
                : request.blocks().stream()
                        .map(b -> new Block(b.type(), b.text(), b.lang(), b.imageId()))
                        .toList();
        return ApiResponse.ok(documentBlockEditService.save(documentId, request.baseVersionId(), blocks));
    }

    /** 块编辑插入图片：上传到私有 OSS，返回 imageId 供块保存时引用。 */
    @PostMapping(value = "/{documentId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_EDIT)
    public ApiResponse<ImageView> uploadImage(@PathVariable("documentId") String documentId,
                                              @RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "图片读取失败");
        }
        return ApiResponse.ok(documentImageService.upload(documentId,
                new Upload(file.getOriginalFilename(), file.getContentType(), file.getSize(), bytes)));
    }

    /** 图片代理：`<img>` 无法携带 Authorization，前端取 Blob 后本地展示。 */
    @GetMapping("/{documentId}/images/{imageId}")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ResponseEntity<byte[]> image(@PathVariable("documentId") String documentId,
                                        @PathVariable("imageId") String imageId) {
        var content = documentImageService.load(documentId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.bytes());
    }

    /** 当前版本按结构层导出 Word（模板样式优先；只还原语义结构，不保证页面版式）。 */
    @GetMapping("/{documentId}/exports/docx")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ResponseEntity<byte[]> exportDocx(@PathVariable("documentId") String documentId) {
        byte[] bytes = documentWordExportService.exportDocx(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.docx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    @GetMapping("/{documentId}/versions/{versionId}/file")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public org.springframework.http.ResponseEntity<byte[]> versionFile(
            @PathVariable("documentId") String documentId,
            @PathVariable("versionId") String versionId) {
        var content = documentQueryService.versionContent(documentId, versionId);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(content.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.content());
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

    /** 只重发当前版本向量化事件（不重解析、不改结构）。 */
    @PostMapping("/{documentId}/reindex")
    @RequirePermission(PermissionCode.DOCUMENT_EDIT)
    public ApiResponse<ParseResponse> reindex(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(new ParseResponse(documentLifecycleService.reindexEmbedding(documentId)));
    }

    /** 块编辑请求：baseVersionId 用于乐观锁；blocks 按顺序构成整篇文本。 */
    public record BlockEditRequest(String baseVersionId, java.util.List<BlockRequest> blocks) {
    }

    public record BlockRequest(String type, String text, String lang, String imageId) {
    }
}
