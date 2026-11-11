package com.cidas.edudoc.message.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NOTIFICATION_REQUESTED 事件的常量与载荷装配（06 切片 2-4/2-5，设计文档 §四）。
 *
 * <p>载荷只带定位信息：消息类型、去重聚合 id、可选扫描日期、资源定位、触发人、收件人清单、
 * 已渲染的标题/正文——不塞整段业务对象。标题/正文由生产者渲染（总分等业务事实只有生产者手上有），
 * 去重键由 {@link NotificationWorker} 消费时拼装。字段名改动会静默断链，勿单改一侧。
 */
public final class NotificationEvents {

    /** Outbox 事件类型；漏登记 application.yml 的 stream-keys 会被调度器直接置 FAILED（§4.2）。 */
    public static final String EVENT_TYPE = "NOTIFICATION_REQUESTED";

    /** 消息类型与库约束 ck_notification_type / 偏好口径一致（本切片只产这三类）。 */
    public static final String TYPE_SUBMISSION = "SUBMISSION";
    public static final String TYPE_FEEDBACK = "FEEDBACK";
    public static final String TYPE_FORMAT = "FORMAT";

    private NotificationEvents() {
    }

    /**
     * 装配事件载荷。可空参数：{@code dedupDate}（仅临近截止提醒带扫描日，保证每天最多一封）、
     * {@code actorId}（系统触发为 null，前端显示「系统」）。
     *
     * @param notifType    消息类型（SUBMISSION/FEEDBACK/FORMAT，决定收件人偏好过滤）
     * @param aggregateRef 去重聚合 id（提交/批改=submissionId，格式=checkId，截止提醒=assignmentId）
     * @param dedupDate    扫描日期 yyyyMMdd，可为 null
     * @param recipientIds 收件人主键清单（生产者在业务事务内解析好，worker 只逐人投递）
     */
    public static Map<String, Object> payload(String notifType, String aggregateRef, String dedupDate,
                                              String resourceType, String resourceRef, String actorId,
                                              List<String> recipientIds, String title, String body) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("notifType", notifType);
        map.put("aggregateRef", aggregateRef);
        map.put("dedupDate", dedupDate);
        map.put("resourceType", resourceType);
        map.put("resourceRef", resourceRef);
        map.put("actorId", actorId);
        map.put("recipientIds", recipientIds);
        map.put("title", title);
        map.put("body", body);
        return map;
    }
}
