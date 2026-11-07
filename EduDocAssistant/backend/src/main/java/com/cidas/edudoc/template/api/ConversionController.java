package com.cidas.edudoc.template.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.template.application.ConversionService;
import com.cidas.edudoc.template.application.ConversionService.ConversionView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 格式转换端点（详细设计表 3-6，D-06；批量=前端循环多任务）。 */
@RestController
@RequestMapping("/api/v1/conversions")
@RequiredArgsConstructor
public class ConversionController {

    private final ConversionService conversionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.FORMAT_CONVERT)
    public ApiResponse<ConversionView> create(@RequestBody CreateRequest request) {
        return ApiResponse.ok(conversionService.create(request.documentId(), request.targetFormat()));
    }

    @GetMapping("/{taskId}")
    @RequirePermission(PermissionCode.FORMAT_CONVERT)
    public ApiResponse<ConversionView> get(@PathVariable("taskId") String taskId) {
        return ApiResponse.ok(conversionService.get(taskId));
    }

    @GetMapping
    @RequirePermission(PermissionCode.FORMAT_CONVERT)
    public ApiResponse<List<ConversionView>> listByDocument(
            @RequestParam("documentId") String documentId) {
        return ApiResponse.ok(conversionService.listByDocument(documentId));
    }

    /** 创建请求体：单文档单方向。 */
    record CreateRequest(String documentId, String targetFormat) {
    }
}
