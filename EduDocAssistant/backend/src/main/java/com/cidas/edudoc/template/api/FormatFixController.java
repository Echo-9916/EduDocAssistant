package com.cidas.edudoc.template.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.template.application.FormatFixService;
import com.cidas.edudoc.template.application.FormatFixService.FixView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 一键修复端点（详细设计表 3-6 fix，D-05）。 */
@RestController
@RequiredArgsConstructor
public class FormatFixController {

    private final FormatFixService formatFixService;

    @PostMapping("/api/v1/format-checks/{checkId}/fix")
    @RequirePermission(PermissionCode.DOCUMENT_EDIT)
    public ApiResponse<FixView> fix(@PathVariable("checkId") String checkId,
                                    @RequestBody FixRequest request) {
        return ApiResponse.ok(formatFixService.fix(checkId, request.issueIds()));
    }

    /** 修复请求体：仅 auto_fixable=Y 且 OPEN 的问题生效，其余原样回执 skipped。 */
    record FixRequest(List<String> issueIds) {
    }
}
