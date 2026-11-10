package com.cidas.edudoc.message.application;

import com.cidas.edudoc.common.api.PageResult;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 站内信箱应用服务（06 切片 2）：列表与未读数、批量已读/全部已读、通知偏好与投递写入端口。
 *
 * <p>响应视图一律本接口嵌套 record（照 coursework 惯例）；已读语义为
 * {@code read_at} 非空即已读。偏好口径：<b>无偏好记录 = 接收</b>（enabled 默认 true）。
 */
public interface InboxService {

    /** 本人信箱分页列表，默认 create_time DESC。 */
    PageResult<MessageView> pageMessages(MessageQuery query);

    /** 本人未读数（工作台角标），与列表 unread=true 同口径。 */
    long unreadCount();

    /**
     * 批量已读/未读：只更新属于当前用户的行，越权 id 静默忽略，
     * {@code updated} 只算命中自己的条数，不报错也不泄露他人消息存在性。
     */
    ReadResult markRead(ReadCommand command);

    /** 全部已读：把本人所有未读置为已读，返回实际改动行数。 */
    ReadResult markAllRead();

    /** 八类偏好全量返回；无记录的类型 enabled=true（缺省即接收）。 */
    List<PreferenceView> listPreferences();

    /** 幂等保存偏好（撞 uk_notif_pref_1 不冒 500），返回写入/更新的条数。 */
    int savePreferences(List<PreferenceItem> items);

    /**
     * 投递写入端口（给 2-4 Worker / 2-5 生产者调用，本切片不接事件）：
     * 先按收件人偏好过滤（该 type 关了就返回 false 且不写行），
     * 再撞 uk_notification_1 去重（重复 dedupKey 返回 false 且不写第二行）。
     *
     * @return true 表示新写一行；false 表示偏好关闭或已存在
     */
    boolean deliver(DeliverCommand cmd);

    record MessageQuery(String type, Boolean unread, Integer page, Integer pageSize) {
    }

    /** actorName 左连 edudoc_user.display_name，系统消息为 null → 前端显示「系统」。 */
    record MessageView(String id, String type, String title, String body, String resourceType,
                       String resourceRef, String actorName, OffsetDateTime createdAt, boolean read) {
    }

    record ReadCommand(List<String> ids, Boolean read) {
    }

    record ReadResult(int updated) {
    }

    record PreferenceView(String type, boolean enabled) {
    }

    record PreferenceItem(String type, Boolean enabled) {
    }

    record DeliverCommand(String recipientId, String type, String title, String body,
                          String resourceType, String resourceRef, String actorId, String dedupKey) {
    }
}
