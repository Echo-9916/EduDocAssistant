package com.cidas.edudoc.template.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.template.application.TemplateService;
import com.cidas.edudoc.template.application.TemplateService.CreateCommand;
import com.cidas.edudoc.template.application.TemplateService.ListQuery;
import com.cidas.edudoc.template.application.TemplateService.TemplateView;
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

/** 模板主档端点（详细设计表 3-6 前两个 + 停用/删除辅助端点，D-01）。 */
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final com.cidas.edudoc.template.application.TemplateProvisionService templateProvisionService;
    private final com.cidas.edudoc.template.application.TemplateVersionService templateVersionService;

    /** 学生只读要求页数据源（1106 审查 P2-1，TEMPLATE_READ + 课程内可见）。 */
    @GetMapping("/{templateId}/current-rules")
    @RequirePermission(PermissionCode.TEMPLATE_READ)
    public ApiResponse<java.util.List<com.cidas.edudoc.template.application.TemplateVersionService.RuleView>>
            currentRules(@PathVariable("templateId") String templateId) {
        return ApiResponse.ok(templateVersionService.currentRules(templateId));
    }

    /** 按模板新建文档（D-07/J4）：学生 DOCUMENT_CREATE 权限 + 课程成员口径。 */
    @PostMapping("/{templateId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.DOCUMENT_CREATE)
    public ApiResponse<com.cidas.edudoc.template.application.TemplateProvisionService.ProvisionView>
            provisionFromTemplate(@PathVariable("templateId") String templateId,
                                  @RequestBody ProvisionRequest request) {
        return ApiResponse.ok(templateProvisionService.provision(templateId,
                request.title(), request.docType()));
    }

    /** 新建请求体：{title, docType}。 */
    record ProvisionRequest(String title, String docType) {
    }

    @GetMapping
    @RequirePermission(PermissionCode.TEMPLATE_READ)
    public ApiResponse<PageResult<TemplateView>> list(
            @RequestParam(name = "courseId", required = false) String courseId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(templateService.list(new ListQuery(courseId, status, page, pageSize)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<TemplateView> create(@RequestBody CreateCommand command) {
        return ApiResponse.ok(templateService.create(command));
    }

    @PatchMapping("/{templateId}/status")
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public ApiResponse<TemplateView> changeStatus(@PathVariable("templateId") String templateId,
                                                  @RequestBody StatusRequest request) {
        templateService.changeStatus(templateId, request.status());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(PermissionCode.TEMPLATE_MANAGE)
    public void deleteDraft(@PathVariable("templateId") String templateId) {
        templateService.deleteDraft(templateId);
    }

    /** 状态迁移请求体：P 发布 / A 停用。 */
    record StatusRequest(String status) {
    }
}
