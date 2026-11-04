package com.cidas.edudoc.ai.api;

import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.ai.application.QaService;
import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.security.RequirePermission;
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

/** 问答端点（表 3-4）：会话创建/回看 + 同步提问（拒答 200 业务成功；超时 504 由错误码映射）。 */
@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    public record CreateSessionRequest(String documentId, String title) {
    }

    public record AskRequest(String question) {
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.AI_QA)
    public ApiResponse<QaService.SessionView> createSession(@RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(qaService.createSession(request.documentId(), request.title()));
    }

    @GetMapping("/sessions")
    @RequirePermission(PermissionCode.AI_QA)
    public ApiResponse<List<QaService.SessionView>> listSessions(
            @RequestParam("documentId") String documentId) {
        return ApiResponse.ok(qaService.listSessions(documentId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @RequirePermission(PermissionCode.AI_QA)
    public ApiResponse<QaService.AskResult> ask(@PathVariable("sessionId") String sessionId,
                                                @RequestBody AskRequest request) {
        return ApiResponse.ok(qaService.ask(sessionId, request.question()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @RequirePermission(PermissionCode.AI_QA)
    public ApiResponse<List<QaService.MessageView>> messages(@PathVariable("sessionId") String sessionId) {
        return ApiResponse.ok(qaService.messages(sessionId));
    }
}
