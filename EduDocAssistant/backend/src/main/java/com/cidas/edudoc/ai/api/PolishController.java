package com.cidas.edudoc.ai.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.ai.application.PolishService;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 润色端点（表 3-4）：同步对照 + 决策（采纳成版/放弃/撤销）。 */
@RestController
@RequiredArgsConstructor
public class PolishController {

    private final PolishService polishService;

    public record ParagraphDto(String chunkId, String text) {
    }

    public record PolishRequest(List<ParagraphDto> paragraphs) {
    }

    public record DecisionRequest(String decision, String acceptedText) {
    }

    @PostMapping("/api/v1/documents/{documentId}/polish")
    @RequirePermission(PermissionCode.AI_POLISH)
    public ApiResponse<List<PolishService.PolishRowView>> polish(
            @PathVariable("documentId") String documentId,
            @RequestBody PolishRequest request) {
        List<String> texts = request.paragraphs() == null ? List.of()
                : request.paragraphs().stream().map(ParagraphDto::text).toList();
        return ApiResponse.ok(polishService.polish(documentId, texts));
    }

    @PostMapping("/api/v1/polish/{polishId}/decision")
    @RequirePermission(PermissionCode.AI_POLISH)
    public ApiResponse<PolishService.AppliedView> decide(
            @PathVariable("polishId") String polishId,
            @RequestBody DecisionRequest request) {
        String decision = request.decision() == null ? "" : request.decision().strip().toUpperCase();
        switch (decision) {
            case "ACCEPT", "PARTIAL" -> {
                return ApiResponse.ok(polishService.apply(polishId, decision, request.acceptedText()));
            }
            case "REJECT" -> {
                polishService.reject(polishId);
                return ApiResponse.ok(null);
            }
            case "REVERT" -> {
                polishService.revert(polishId);
                return ApiResponse.ok(null);
            }
            default -> throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                    "decision 仅支持 ACCEPT|PARTIAL|REJECT|REVERT");
        }
    }
}
