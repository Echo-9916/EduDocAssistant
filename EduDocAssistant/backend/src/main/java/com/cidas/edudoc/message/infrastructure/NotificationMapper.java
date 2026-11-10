package com.cidas.edudoc.message.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * message 域 SQL 入口（全部语句在 {@code resources/mapper/NotificationMapper.xml}，Java 侧不写 SQL）。
 *
 * <p>读写横跨 edudoc_notification / edudoc_notification_preference 两张表，集中一个 Mapper
 * （照 {@code CourseworkMapper} 惯例）。主键出入参一律 32 位大写十六进制串
 * （{@code RAWTOHEX}/{@code HEXTORAW} 转换在 XML 内完成），调用方必须保证进入
 * {@code HEXTORAW} 的入参已过 {@code Ids.isHex16}，否则触发数据库异常。
 */
@Mapper
public interface NotificationMapper {

    // ---------- 信箱（edudoc_notification） ----------

    List<Notification.Inbox> selectInbox(@Param("userIdHex") String userIdHex,
                                         @Param("type") String type,
                                         @Param("unread") Boolean unread,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    long countInbox(@Param("userIdHex") String userIdHex,
                    @Param("type") String type,
                    @Param("unread") Boolean unread);

    /** 未读数（工作台角标）：与列表 unread=true 同口径（recipient_id = 本人且 read_at IS NULL）。 */
    long countUnread(@Param("userIdHex") String userIdHex);

    /** 投递写入：主键由 SYS_GUID 生成；撞 uk_notification_1 由仓储翻译成「已存在」。 */
    int insertNotification(@Param("recipientIdHex") String recipientIdHex,
                           @Param("notifType") String notifType,
                           @Param("title") String title,
                           @Param("body") String body,
                           @Param("resourceType") String resourceType,
                           @Param("resourceRef") String resourceRef,
                           @Param("actorIdHex") String actorIdHex,
                           @Param("dedupKey") String dedupKey);

    /** 批量已读/未读：recipient_id 圈定本人，越权 id 天然不命中，返回命中行数。 */
    int updateRead(@Param("userIdHex") String userIdHex,
                   @Param("idsHex") List<String> idsHex,
                   @Param("read") boolean read);

    /** 全部已读：只改当前未读行，返回实际改动行数。 */
    int markAllRead(@Param("userIdHex") String userIdHex);

    // ---------- 偏好（edudoc_notification_preference） ----------

    /** 本人 INBOX 通道偏好行；无记录 = 接收的口径在应用层落实。 */
    List<Notification.Preference> selectPreferences(@Param("userIdHex") String userIdHex);

    /** MERGE 幂等 upsert：撞 uk_notif_pref_1 不冒异常。 */
    int upsertPreference(@Param("userIdHex") String userIdHex,
                         @Param("notifType") String notifType,
                         @Param("enabled") String enabled);
}
