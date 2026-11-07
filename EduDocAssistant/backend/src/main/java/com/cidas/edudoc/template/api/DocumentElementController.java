package com.cidas.edudoc.template.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.template.application.DocumentStructureService;
import com.cidas.edudoc.template.application.DocumentStructureService.ElementView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 结构元素端点（D-03 契约外辅助：学生「代码与图表规范化」页数据源）。 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentElementController {

    private final DocumentStructureService documentStructureService;

    @GetMapping("/{documentId}/elements")
    @RequirePermission(PermissionCode.DOCUMENT_READ)
    public ApiResponse<List<ElementView>> elements(@PathVariable("documentId") String documentId) {
        return ApiResponse.ok(documentStructureService.listElements(documentId));
    }
}
