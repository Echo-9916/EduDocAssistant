package com.cidas.edudoc.message.infrastructure;

import com.cidas.edudoc.common.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 信箱仓储：SQL 全在 {@link NotificationMapper} 与 XML，本类只做分页装配与唯一约束翻译。
 *
 * <p>主键格式校验（{@code Ids.isHex16}）按既有惯例留在应用服务层，仓储不重复校验；
 * 写路径唯一的翻译是撞 {@code uk_notification_1(recipient_id, dedup_key)} → false，
 * 这是投递幂等语义（重投不产生第二封信、不报错），不是 40902 冲突。
 */
@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final NotificationMapper mapper;

    public PageResult<Notification.Inbox> pageInbox(String userIdHex, String type, Boolean unread,
                                                    int page, int pageSize) {
        long total = mapper.countInbox(userIdHex, type, unread);
        List<Notification.Inbox> items = mapper.selectInbox(userIdHex, type, unread,
                (page - 1) * pageSize, pageSize);
        return new PageResult<>(items, total, page, pageSize);
    }

    public long countUnread(String userIdHex) {
        return mapper.countUnread(userIdHex);
    }

    /** 幂等插入：true = 新写一行；false = dedupKey 已存在（撞唯一键，静默吞掉）。 */
    public boolean insertIfAbsent(String recipientIdHex, String notifType, String title, String body,
                                  String resourceType, String resourceRef, String actorIdHex,
                                  String dedupKey) {
        try {
            mapper.insertNotification(recipientIdHex, notifType, title, body, resourceType,
                    resourceRef, actorIdHex, dedupKey);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public int updateRead(String userIdHex, List<String> idsHex, boolean read) {
        return mapper.updateRead(userIdHex, idsHex, read);
    }

    public int markAllRead(String userIdHex) {
        return mapper.markAllRead(userIdHex);
    }
}
