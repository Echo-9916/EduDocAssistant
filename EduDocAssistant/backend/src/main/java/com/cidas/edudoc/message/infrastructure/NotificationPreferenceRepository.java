package com.cidas.edudoc.message.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通知偏好仓储（channel 本期只有 INBOX；EMAIL 只留值不实现投递）。
 *
 * <p>口径（与设计文档 §2.2 / 库表注释一致）：<b>无偏好记录 = 接收</b>，
 * 只有显式 {@code enabled='N'} 才算关闭。
 */
@Repository
@RequiredArgsConstructor
public class NotificationPreferenceRepository {

    private final NotificationMapper mapper;

    public List<Notification.Preference> list(String userIdHex) {
        return mapper.selectPreferences(userIdHex);
    }

    /** 该类型是否接收：无记录 = 接收（缺省口径），仅显式 'N' 关闭。 */
    public boolean isInboxEnabled(String userIdHex, String notifType) {
        return list(userIdHex).stream()
                .noneMatch(p -> notifType.equals(p.getNotifType()) && "N".equals(p.getEnabled()));
    }

    /** 幂等 upsert（XML 用 MERGE）：撞 uk_notif_pref_1 走 UPDATE 分支，不冒 500。 */
    public void upsertInbox(String userIdHex, String notifType, String enabledYn) {
        mapper.upsertPreference(userIdHex, notifType, enabledYn);
    }
}
