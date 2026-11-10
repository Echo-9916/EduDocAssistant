package com.cidas.edudoc.message.api;

import com.cidas.edudoc.common.api.ApiResponse;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.message.application.InboxService;
import com.cidas.edudoc.message.application.InboxService.MessageQuery;
import com.cidas.edudoc.message.application.InboxService.MessageView;
import com.cidas.edudoc.message.application.InboxService.PreferenceItem;
import com.cidas.edudoc.message.application.InboxService.PreferenceView;
import com.cidas.edudoc.message.application.InboxService.ReadCommand;
import com.cidas.edudoc.message.application.InboxService.ReadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内信箱端点（06 切片 2）：列表/未读数/批量已读/全部已读 + 通知偏好。
 * 认证即可（本人数据，SQL 按当前用户圈定），不加权限点；
 * 本类只做请求体到应用服务命令的装配与 {@code ApiResponse} 封装，校验全部在服务层。
 * 写接口的 Idempotency-Key 由全局 filter 强制，这里不重复处理。
 */
@RestController
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    @GetMapping("/api/v1/messages")
    public ApiResponse<PageResult<MessageView>> messages(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "unread", required = false) Boolean unread,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(inboxService.pageMessages(new MessageQuery(type, unread, page, pageSize)));
    }

    /** 未读数单独口径（工作台角标轮询用），避免前端拉全量列表。 */
    @GetMapping("/api/v1/messages/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(inboxService.unreadCount());
    }

    /** 批量已读/未读：越权 id 静默忽略，updated 只算命中自己的条数。 */
    @PatchMapping("/api/v1/messages:read")
    public ApiResponse<ReadResult> markRead(@RequestBody ReadRequest request) {
        return ApiResponse.ok(inboxService.markRead(new ReadCommand(request.ids(), request.read())));
    }

    /** 全部已读（body 可空）。 */
    @PatchMapping("/api/v1/messages:read-all")
    public ApiResponse<ReadResult> markAllRead() {
        return ApiResponse.ok(inboxService.markAllRead());
    }

    @GetMapping("/api/v1/me/notification-preferences")
    public ApiResponse<List<PreferenceView>> notificationPreferences() {
        return ApiResponse.ok(inboxService.listPreferences());
    }

    @PutMapping("/api/v1/me/notification-preferences")
    public ApiResponse<Integer> saveNotificationPreferences(@RequestBody PreferenceSaveRequest request) {
        List<PreferenceItem> items = request.items() == null ? List.of()
                : request.items().stream()
                        .map(item -> new PreferenceItem(item.type(), item.enabled()))
                        .toList();
        return ApiResponse.ok(inboxService.savePreferences(items));
    }

    // ---------- 请求体（仅本 Controller 使用，不另开 dto 包） ----------

    /** 批量已读请求体：read=true 已读、false 未读；ids 空数组由服务层拒 40001。 */
    public record ReadRequest(List<String> ids, Boolean read) {
    }

    /** 偏好保存请求体：逐项幂等 upsert。 */
    public record PreferenceSaveRequest(List<PreferenceItemRequest> items) {
    }

    public record PreferenceItemRequest(String type, Boolean enabled) {
    }
}
