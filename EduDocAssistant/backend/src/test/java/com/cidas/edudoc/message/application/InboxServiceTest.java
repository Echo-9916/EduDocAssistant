package com.cidas.edudoc.message.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.message.application.InboxService.DeliverCommand;
import com.cidas.edudoc.message.application.InboxService.MessageQuery;
import com.cidas.edudoc.message.application.InboxService.MessageView;
import com.cidas.edudoc.message.application.InboxService.PreferenceItem;
import com.cidas.edudoc.message.application.InboxService.PreferenceView;
import com.cidas.edudoc.message.application.InboxService.ReadCommand;
import com.cidas.edudoc.message.application.InboxService.ReadResult;
import com.cidas.edudoc.message.application.impl.InboxServiceImpl;
import com.cidas.edudoc.message.infrastructure.Notification;
import com.cidas.edudoc.message.infrastructure.NotificationPreferenceRepository;
import com.cidas.edudoc.message.infrastructure.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 站内信箱服务：deliver 幂等与偏好过滤、批量已读越权/空参、列表与未读数口径、偏好缺省即接收。 */
class InboxServiceTest {

    private static final String HEX_USER = "A".repeat(32);
    private static final String HEX_ACTOR = "B".repeat(32);
    private static final String HEX_MSG = "C".repeat(32);
    private static final String HEX_OTHER_MSG = "D".repeat(32);

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationPreferenceRepository preferenceRepository =
            mock(NotificationPreferenceRepository.class);
    private final InboxService service = new InboxServiceImpl(notificationRepository, preferenceRepository);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private static void login() {
        AuthContext.set(new UserPrincipal(HEX_USER, "student01", "张三", Set.of("STUDENT"), Set.of(), Set.of()));
    }

    private static DeliverCommand deliverCommand() {
        return new DeliverCommand(HEX_USER, "SUBMISSION", "新提交", "张三提交了作业",
                "ASSIGNMENT", HEX_OTHER_MSG, HEX_ACTOR, "SUBMISSION:asg1:" + HEX_USER);
    }

    private static Notification.Inbox inboxRow(String id, OffsetDateTime readAt, String actorName) {
        Notification.Inbox row = new Notification.Inbox();
        row.setId(id);
        row.setType("SUBMISSION");
        row.setTitle("新提交");
        row.setActorName(actorName);
        row.setCreatedAt(OffsetDateTime.parse("2026-11-10T09:00:00+08:00"));
        row.setReadAt(readAt);
        return row;
    }

    // ---------- deliver：幂等与偏好 ----------

    @Test
    void deliverNewRowReturnsTrue() {
        when(preferenceRepository.isInboxEnabled(HEX_USER, "SUBMISSION")).thenReturn(true);
        when(notificationRepository.insertIfAbsent(eq(HEX_USER), eq("SUBMISSION"), anyString(),
                anyString(), anyString(), anyString(), eq(HEX_ACTOR), anyString())).thenReturn(true);

        assertTrue(service.deliver(deliverCommand()));
    }

    @Test
    void deliverDuplicateDedupKeyReturnsFalseWithoutSecondRow() {
        when(preferenceRepository.isInboxEnabled(HEX_USER, "SUBMISSION")).thenReturn(true);
        // 撞 uk_notification_1 由仓储翻译成 false：不报错、不写第二行
        when(notificationRepository.insertIfAbsent(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyString())).thenReturn(false);

        assertFalse(service.deliver(deliverCommand()));
        verify(notificationRepository).insertIfAbsent(eq(HEX_USER), eq("SUBMISSION"), anyString(),
                anyString(), anyString(), anyString(), eq(HEX_ACTOR), anyString());
    }

    @Test
    void deliverSkipsWriteWhenPreferenceDisabled() {
        when(preferenceRepository.isInboxEnabled(HEX_USER, "SUBMISSION")).thenReturn(false);

        assertFalse(service.deliver(deliverCommand()));
        verify(notificationRepository, never()).insertIfAbsent(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void deliverRejectsInvalidIdsBeforeHexToRaw() {
        // 信任边界：非法 hex 进 HEXTORAW 会炸库，必须 40001 拦下且不触写路径
        var e = assertThrows(CidasBusinessException.class, () -> service.deliver(
                new DeliverCommand("not-a-hex", "SUBMISSION", "标题", null,
                        null, null, null, "k1")));
        assertEquals(40001, e.errorCode().code());
        verify(notificationRepository, never()).insertIfAbsent(any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    // ---------- 批量已读 ----------

    @Test
    void markReadEmptyIdsRejected40001() {
        login();

        var e = assertThrows(CidasBusinessException.class,
                () -> service.markRead(new ReadCommand(List.of(), true)));

        assertEquals(40001, e.errorCode().code());
        verify(notificationRepository, never()).updateRead(anyString(), anyList(), anyBoolean());
    }

    @Test
    void markReadCountsOnlyOwnRowsAndSilentlySkipsOthers() {
        login();
        // 两个 id 只命中 1 条：越权 id 由 SQL 的 recipient_id 圈定静默忽略，updated 只算自己的
        when(notificationRepository.updateRead(eq(HEX_USER), anyList(), eq(true))).thenReturn(1);

        ReadResult result = service.markRead(new ReadCommand(List.of(HEX_MSG, HEX_OTHER_MSG), true));

        assertEquals(1, result.updated());
        verify(notificationRepository).updateRead(HEX_USER, List.of(HEX_MSG, HEX_OTHER_MSG), true);
    }

    @Test
    void markReadFiltersInvalidHexBeforeSql() {
        login();
        when(notificationRepository.updateRead(eq(HEX_USER), anyList(), eq(true))).thenReturn(1);

        service.markRead(new ReadCommand(List.of(HEX_MSG, "zzz"), true));

        verify(notificationRepository).updateRead(HEX_USER, List.of(HEX_MSG), true);
    }

    @Test
    void markAllReadDelegatesWithCurrentUserId() {
        login();
        when(notificationRepository.markAllRead(HEX_USER)).thenReturn(3);

        assertEquals(3, service.markAllRead().updated());
    }

    // ---------- 列表与未读数 ----------

    @Test
    void pageMessagesDerivesReadFlagAndKeepsPaging() {
        login();
        when(notificationRepository.pageInbox(HEX_USER, "SUBMISSION", true, 2, 20))
                .thenReturn(new PageResult<>(List.of(
                        inboxRow(HEX_MSG, null, "李老师"),
                        inboxRow(HEX_OTHER_MSG, OffsetDateTime.parse("2026-11-10T10:00:00+08:00"), null)),
                        42, 2, 20));

        PageResult<MessageView> result = service.pageMessages(new MessageQuery("SUBMISSION", true, 2, 20));

        assertEquals(42, result.total());
        assertEquals(2, result.page());
        assertEquals(20, result.pageSize());
        MessageView unread = result.items().get(0);
        assertFalse(unread.read());
        assertEquals("李老师", unread.actorName());
        assertEquals(OffsetDateTime.parse("2026-11-10T09:00:00+08:00"), unread.createdAt());
        // 系统消息 actorName 为 null（前端显示「系统」），read_at 非空即已读
        MessageView read = result.items().get(1);
        assertTrue(read.read());
        assertNull(read.actorName());
    }

    @Test
    void pageMessagesRejectsUnknownType() {
        login();

        var e = assertThrows(CidasBusinessException.class,
                () -> service.pageMessages(new MessageQuery("HACK", null, null, null)));

        assertEquals(40001, e.errorCode().code());
        verify(notificationRepository, never()).pageInbox(anyString(), anyString(), any(), eq(1), eq(10));
    }

    @Test
    void unreadCountUsesSameUserScopeAsList() {
        login();
        when(notificationRepository.countUnread(HEX_USER)).thenReturn(7L);

        assertEquals(7L, service.unreadCount());
        // 口径一致：unread-count 与列表都按 recipient_id = 当前用户
        verify(notificationRepository).countUnread(HEX_USER);
    }

    // ---------- 偏好 ----------

    @Test
    void listPreferencesDefaultsToEnabledWithoutRows() {
        login();
        when(preferenceRepository.list(HEX_USER)).thenReturn(List.of(preference("FORMAT", "N")));

        List<PreferenceView> views = service.listPreferences();

        assertEquals(8, views.size());
        // 无偏好记录 = 接收：只有显式 'N' 的 FORMAT 是关
        assertEquals(false, views.stream().filter(v -> v.type().equals("FORMAT")).findFirst().orElseThrow().enabled());
        assertTrue(views.stream().filter(v -> !v.type().equals("FORMAT")).allMatch(PreferenceView::enabled));
    }

    @Test
    void savePreferencesUpsertsYesNoPerItem() {
        login();

        int updated = service.savePreferences(List.of(
                new PreferenceItem("COURSE", true), new PreferenceItem("ALERT", false)));

        assertEquals(2, updated);
        verify(preferenceRepository).upsertInbox(HEX_USER, "COURSE", "Y");
        verify(preferenceRepository).upsertInbox(HEX_USER, "ALERT", "N");
    }

    @Test
    void savePreferencesRejectsUnknownType() {
        login();

        var e = assertThrows(CidasBusinessException.class,
                () -> service.savePreferences(List.of(new PreferenceItem("HACK", true))));

        assertEquals(40001, e.errorCode().code());
        verify(preferenceRepository, never()).upsertInbox(anyString(), anyString(), anyString());
    }

    private static Notification.Preference preference(String type, String enabled) {
        Notification.Preference row = new Notification.Preference();
        row.setNotifType(type);
        row.setEnabled(enabled);
        return row;
    }
}
