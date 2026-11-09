package com.cidas.edudoc.template.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.template.application.TemplateVersionService;
import com.cidas.edudoc.template.application.TemplateVersionService.RuleCommand;
import com.cidas.edudoc.template.application.TemplateVersionService.TemplateVersionView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 模板版本与规则端点（详细设计表 3-6 versions/publish + 规则编辑辅助端点，D-02）。 */
@RestController
@RequestMapping("/api/v1/templates/{templateId}/versions")
@RequiredArgsConstructor
public class TemplateVersionController {

    private final TemplateVersionService templateVersionService;

    @GetMapping
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<List<TemplateVersionView>> list(@PathVariable("templateId") String templateId) {
        return ApiResponse.ok(templateVersionService.listVersions(templateId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<TemplateVersionView> create(@PathVariable("templateId") String templateId,
                                                   @RequestPart(value = "file", required = false) MultipartFile file,
                                                   @RequestParam(value = "changeSummary", required = false)
                                                   String changeSummary) throws IOException {
        byte[] bytes = file == null || file.isEmpty() ? null : file.getBytes();
        String fileName = file == null ? null : file.getOriginalFilename();
        return ApiResponse.ok(templateVersionService.create(templateId, changeSummary, bytes, fileName));
    }

    /** 验收反馈：编辑既有版本另存为新版本（body 带基版本、全量规则与可选说明）。 */
    @PostMapping("/fork")
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<TemplateVersionService.TemplateVersionView> fork(
            @PathVariable("templateId") String templateId,
            @RequestBody ForkRequest request) {
        return ApiResponse.ok(templateVersionService.fork(templateId, request.baseVersionId(),
                request.rules(), request.changeSummary()));
    }

    /** 规则另存请求：rules 为全量替换语义（走与保存闸同一套校验）。 */
    public record ForkRequest(String baseVersionId,
                              List<com.cidas.edudoc.template.application.TemplateVersionService.RuleCommand> rules,
                              String changeSummary) {
    }

    /** 验收决议①：上传预览——解析规则草稿即返即走，不产生版本；确认后再调 POST /versions。 */
    @PostMapping("/preview")
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<List<TemplateVersionService.RuleView>> previewRules(
            @PathVariable("templateId") String templateId,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        byte[] bytes = file == null || file.isEmpty() ? null : file.getBytes();
        String fileName = file == null ? null : file.getOriginalFilename();
        return ApiResponse.ok(templateVersionService.previewRules(templateId, bytes, fileName));
    }

    @GetMapping("/{versionId}/rules")
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<List<TemplateVersionService.RuleView>> listRules(
            @PathVariable("templateId") String templateId,
            @PathVariable("versionId") String versionId) {
        return ApiResponse.ok(templateVersionService.listRules(templateId, versionId));
    }

    @PutMapping("/{versionId}/rules")
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<Void> replaceRules(@PathVariable("templateId") String templateId,
                                          @PathVariable("versionId") String versionId,
                                          @RequestBody List<RuleCommand> rules) {
        templateVersionService.replaceRules(templateId, versionId, rules);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{versionId}/publish")
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<Void> publish(@PathVariable("templateId") String templateId,
                                     @PathVariable("versionId") String versionId) {
        templateVersionService.publish(templateId, versionId);
        return ApiResponse.ok(null);
    }
}
