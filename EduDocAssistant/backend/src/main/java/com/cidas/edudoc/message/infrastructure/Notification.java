package com.cidas.edudoc.message.infrastructure;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * message 域的查询投影行（MyBatis resultType 用 {@code Notification$Xxx} 嵌套类名）。
 *
 * <p>照 {@code CourseworkRow} 惯例集中一个文件：只是列的搬运工，没有行为、不参与写路径。
 * 主键一律 RAWTOHEX 后的 32 位大写十六进制串（见 {@code common/persistence/Ids}）。
 */
public final class Notification {

    private Notification() {
    }

    /** 信箱列表行：actorName 左连 edudoc_user.display_name，系统消息 actor_id 为空 → null（前端显示「系统」）。 */
    @Data
    public static class Inbox {
        private String id;
        private String type;
        private String title;
        private String body;
        private String resourceType;
        private String resourceRef;
        private String actorName;
        private OffsetDateTime createdAt;
        /** 已读时间，空即未读；read 布尔由应用层派生。 */
        private OffsetDateTime readAt;
    }

    /** 通知偏好行（channel='INBOX'；EMAIL 本期只留值不实现）。 */
    @Data
    public static class Preference {
        private String notifType;
        /** Y/N；无记录 = 接收，缺省口径在应用层与仓储注释写明。 */
        private String enabled;
    }
}
