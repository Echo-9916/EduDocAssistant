package com.cidas.edudoc.message.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.message.application.InboxService;
import com.cidas.edudoc.message.infrastructure.Notification;
import com.cidas.edudoc.message.infrastructure.NotificationPreferenceRepository;
import com.cidas.edudoc.message.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 站内信箱应用服务实现（06 切片 2）。
 *
 * <p>校验顺序照 coursework 惯例：主键格式（{@code Ids.isHex16}，进 HEXTORAW 前必须拦）→ 枚举/参数。
 * 越权消息 id 不在服务层比对归属：SQL 一律按 {@code recipient_id = 当前用户} 圈定，
 * 别人的 id 天然不命中，{@code updated} 只回命中数，不报错也不泄露存在性。
 */
@Service
@RequiredArgsConstructor
public class InboxServiceImpl implements InboxService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    /** 八类消息类型，与库约束 ck_notification_type 及前端 mock/domain/messages.ts 逐字对齐。 */
    private static final List<String> NOTIF_TYPES = List.of(
            "COURSE", "SUBMISSION", "FEEDBACK", "FORMAT", "PERMISSION", "ALERT", "APPROVAL", "SYSTEM");
    /** dedup_key 列宽 VARCHAR2(64)：超长会 ORA-12899，进 SQL 前先拦。 */
    private static final int DEDUP_KEY_MAX = 64;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    // ---------- 信箱 ----------

    @Override
    public PageResult<MessageView> pageMessages(MessageQuery query) {
        UserPrincipal principal = AuthContext.require();
        String type = blankToNull(query.type());
        if (type != null && !NOTIF_TYPES.contains(type)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "未知的消息类型：" + type);
        }
        int page = normalizePage(query.page());
        int pageSize = normalizePageSize(query.pageSize());
        PageResult<Notification.Inbox> result = notificationRepository.pageInbox(
                principal.userId(), type, query.unread(), page, pageSize);
        List<MessageView> items = result.items().stream().map(InboxServiceImpl::toView).toList();
        return new PageResult<>(items, result.total(), page, pageSize);
    }

    @Override
    public long unreadCount() {
        // 与列表 unread=true 同口径：同一 recipient_id、同一 read_at IS NULL 条件（XML 各一条 SQL）
        return notificationRepository.countUnread(AuthContext.require().userId());
    }

    @Override
    public ReadResult markRead(ReadCommand command) {
        UserPrincipal principal = AuthContext.require();
        if (command.ids() == null || command.ids().isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "消息 id 列表不能为空");
        }
        if (command.read() == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "read 标记不能缺省");
        }
        // 非法 hex 的 id 进 HEXTORAW 会炸库，先滤掉（等价于「不存在的消息」静默忽略）
        List<String> ids = command.ids().stream().filter(Ids::isHex16).toList();
        if (ids.isEmpty()) {
            return new ReadResult(0);
        }
        return new ReadResult(notificationRepository.updateRead(principal.userId(), ids, command.read()));
    }

    @Override
    public ReadResult markAllRead() {
        return new ReadResult(notificationRepository.markAllRead(AuthContext.require().userId()));
    }

    // ---------- 偏好 ----------

    @Override
    public List<PreferenceView> listPreferences() {
        Map<String, String> saved = preferenceRepository.list(AuthContext.require().userId()).stream()
                .collect(Collectors.toMap(Notification.Preference::getNotifType,
                        Notification.Preference::getEnabled, (a, b) -> a));
        // 无偏好记录 = 接收：只有显式 'N' 才算关（口径见设计文档 §2.2）
        return NOTIF_TYPES.stream()
                .map(type -> new PreferenceView(type, !"N".equals(saved.get(type))))
                .toList();
    }

    @Override
    @Transactional
    public int savePreferences(List<PreferenceItem> items) {
        UserPrincipal principal = AuthContext.require();
        if (items == null || items.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "偏好项不能为空");
        }
        for (PreferenceItem item : items) {
            if (item.type() == null || !NOTIF_TYPES.contains(item.type())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "未知的消息类型：" + item.type());
            }
            if (item.enabled() == null) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "消息类型 " + item.type() + " 缺少 enabled");
            }
        }
        for (PreferenceItem item : items) {
            // MERGE 幂等 upsert：重复保存同一类型走 UPDATE 分支，撞 uk_notif_pref_1 不冒 500
            preferenceRepository.upsertInbox(principal.userId(), item.type(), item.enabled() ? "Y" : "N");
        }
        return items.size();
    }

    // ---------- 投递写入端口（2-4 Worker / 2-5 生产者调用，本切片不接事件） ----------

    @Override
    public boolean deliver(DeliverCommand cmd) {
        // 信任边界：非法 hex 进 HEXTORAW 会触发数据库异常，先拦
        if (!Ids.isHex16(cmd.recipientId())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "收件人 id 格式非法");
        }
        if (cmd.actorId() != null && !Ids.isHex16(cmd.actorId())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "触发人 id 格式非法");
        }
        if (cmd.type() == null || !NOTIF_TYPES.contains(cmd.type())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "未知的消息类型：" + cmd.type());
        }
        if (cmd.title() == null || cmd.title().isBlank()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "消息标题不能为空");
        }
        if (cmd.dedupKey() == null || cmd.dedupKey().isBlank() || cmd.dedupKey().length() > DEDUP_KEY_MAX) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                    "dedupKey 必填且不超过 " + DEDUP_KEY_MAX + " 字符");
        }
        // 偏好过滤：无记录 = 接收；显式关掉该类型 → 不写行直接 false
        if (!preferenceRepository.isInboxEnabled(cmd.recipientId(), cmd.type())) {
            return false;
        }
        // 撞 uk_notification_1(recipient_id, dedup_key) → false：重投不产生第二封信
        return notificationRepository.insertIfAbsent(cmd.recipientId(), cmd.type(), cmd.title(),
                cmd.body(), cmd.resourceType(), cmd.resourceRef(), cmd.actorId(), cmd.dedupKey());
    }

    // ---------- 私有工具 ----------

    private static MessageView toView(Notification.Inbox row) {
        return new MessageView(row.getId(), row.getType(), row.getTitle(), row.getBody(),
                row.getResourceType(), row.getResourceRef(), row.getActorName(),
                row.getCreatedAt(), row.getReadAt() != null);
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /** 缺省 10；非法（&lt;1 或 &gt;100）回落默认（与 coursework 同口径）。 */
    private static int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE ? DEFAULT_PAGE_SIZE : pageSize;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
