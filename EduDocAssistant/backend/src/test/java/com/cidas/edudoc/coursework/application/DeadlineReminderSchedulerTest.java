package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentStats;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.MemberAccount;
import com.cidas.edudoc.message.application.InboxService;
import com.cidas.edudoc.message.application.InboxService.DeliverCommand;
import com.cidas.edudoc.message.application.NotificationWorker;
import com.cidas.edudoc.message.infrastructure.NotificationWorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 2-5 生产者④（临近截止扫描）：48h 窗口现算传入仓储（PUBLISHED/未提交过滤在 SQL，
 * 见 CourseworkMapper.xml selectDueSoonAssignments / selectUnsubmittedMembers）、
 * 收件人=未交名单、同日重扫去重键不变（串真 NotificationWorker 断言 deliver 收到同一个 dedupKey，
 * 第二封由 uk_notification_1 的 false 分支兜底）、大班按载荷上限拆事件。
 */
class DeadlineReminderSchedulerTest {

    private static final String ASSIGNMENT_ID = "A".repeat(32);
    private static final String S1 = "B".repeat(32);
    private static final String S2 = "C".repeat(32);

    private final CourseworkRepository courseworkRepository = mock(CourseworkRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeadlineReminderScheduler scheduler = new DeadlineReminderScheduler(
            courseworkRepository, outboxEventRepository, properties(true));

    private static NotificationWorkerProperties properties(boolean enabled) {
        return new NotificationWorkerProperties(enabled, 1,
                "edudoc:stream:notification", "cg-notification");
    }

    private static AssignmentStats assignment(OffsetDateTime dueTime) {
        AssignmentStats row = new AssignmentStats();
        row.setId(ASSIGNMENT_ID);
        row.setTitle("索引设计实验");
        row.setDueTime(dueTime);
        return row;
    }

    private static MemberAccount student(String userIdHex) {
        MemberAccount member = new MemberAccount();
        member.setUserId(userIdHex);
        return member;
    }

    @Test
    void scanWindowIsNext48Hours() {
        when(courseworkRepository.listDueSoonAssignments(any(), any())).thenReturn(List.of());
        OffsetDateTime before = OffsetDateTime.now();

        scheduler.scanDueSoon();

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(courseworkRepository).listDueSoonAssignments(from.capture(), to.capture());
        assertEquals(Duration.ofHours(48), Duration.between(from.getValue(), to.getValue()));
        assertFalse(from.getValue().isBefore(before));
        assertFalse(from.getValue().isAfter(OffsetDateTime.now()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unsubmittedStudentsBecomeRecipientsWithDatedDedupMaterial() {
        // SQL 只回窗口内 PUBLISHED 作业与未交学生：已交（S2）与超 48h 的在 mapper 侧被滤掉
        when(courseworkRepository.listDueSoonAssignments(any(), any()))
                .thenReturn(List.of(assignment(OffsetDateTime.now().plusHours(30))));
        when(courseworkRepository.listUnsubmitted(ASSIGNMENT_ID)).thenReturn(List.of(student(S1)));

        scheduler.scanDueSoon();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventRepository).publish(eq("ASSIGNMENT"), eq(ASSIGNMENT_ID),
                eq("NOTIFICATION_REQUESTED"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertEquals("SUBMISSION", payload.get("notifType"));
        assertEquals(ASSIGNMENT_ID, payload.get("aggregateRef"));
        assertEquals(DateTimeFormatter.BASIC_ISO_DATE.format(OffsetDateTime.now()), payload.get("dedupDate"));
        assertEquals(List.of(S1), payload.get("recipientIds"));
        assertEquals("ASSIGNMENT", payload.get("resourceType"));
        assertEquals(ASSIGNMENT_ID, payload.get("resourceRef"));
        assertNull(payload.get("actorId")); // 系统触发，前端显示「系统」
        String body = (String) payload.get("body");
        assertTrue(body.contains("索引设计实验"));
        assertTrue(body.contains("截止"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameDayRescanKeepsSameDedupKeySoReplayWritesNoSecondLetter() throws Exception {
        when(courseworkRepository.listDueSoonAssignments(any(), any()))
                .thenReturn(List.of(assignment(OffsetDateTime.now().plusHours(30))));
        when(courseworkRepository.listUnsubmitted(ASSIGNMENT_ID)).thenReturn(List.of(student(S1), student(S2)));

        scheduler.scanDueSoon();
        scheduler.scanDueSoon(); // 同一小时内第二次扫描（或多实例各扫一次）

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventRepository, times(2)).publish(anyString(), anyString(), anyString(), captor.capture());
        // 两条事件的载荷逐字段一致 → worker 拼出的去重键必然相同（含日期段）
        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));

        // 串真 worker 验证到 deliver 层：重放两次，同一收件人拿到同一个 dedupKey；
        // 第二轮 deliver 返回 false（撞 uk_notification_1），不产生第二封信
        InboxService inboxService = mock(InboxService.class);
        when(inboxService.deliver(any())).thenReturn(true).thenReturn(false)
                .thenReturn(true).thenReturn(false);
        NotificationWorker worker = new NotificationWorker(mock(StringRedisTemplate.class), inboxService,
                properties(false), objectMapper);
        for (Map<String, Object> payload : captor.getAllValues()) {
            worker.process(objectMapper.writeValueAsString(payload));
        }
        ArgumentCaptor<DeliverCommand> delivered = ArgumentCaptor.forClass(DeliverCommand.class);
        verify(inboxService, times(4)).deliver(delivered.capture());
        List<String> keys = delivered.getAllValues().stream().map(DeliverCommand::dedupKey).distinct().toList();
        assertEquals(1, keys.size());
        assertTrue(keys.get(0).startsWith("SUBMISSION:" + ASSIGNMENT_ID + ":"));
    }

    @Test
    void fullySubmittedAssignmentSkipsPublish() {
        when(courseworkRepository.listDueSoonAssignments(any(), any()))
                .thenReturn(List.of(assignment(OffsetDateTime.now().plusHours(30))));
        when(courseworkRepository.listUnsubmitted(ASSIGNMENT_ID)).thenReturn(List.of());

        scheduler.scanDueSoon();

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bigClassIsChunkedUnderOutboxPayloadLimit() {
        List<MemberAccount> students = new ArrayList<>();
        for (int i = 1; i <= DeadlineReminderScheduler.RECIPIENTS_PER_EVENT + 1; i++) {
            students.add(student(String.format("%032X", i)));
        }
        when(courseworkRepository.listDueSoonAssignments(any(), any()))
                .thenReturn(List.of(assignment(OffsetDateTime.now().plusHours(30))));
        when(courseworkRepository.listUnsubmitted(ASSIGNMENT_ID)).thenReturn(students);

        scheduler.scanDueSoon();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventRepository, times(2)).publish(anyString(), anyString(), anyString(), captor.capture());
        assertEquals(DeadlineReminderScheduler.RECIPIENTS_PER_EVENT,
                ((List<String>) captor.getAllValues().get(0).get("recipientIds")).size());
        assertEquals(1, ((List<String>) captor.getAllValues().get(1).get("recipientIds")).size());
        // 拆开的两事件共用同一去重材料：同班学生无论落在哪个事件，键都一致
        assertEquals(captor.getAllValues().get(0).get("dedupDate"),
                captor.getAllValues().get(1).get("dedupDate"));
    }

    @Test
    void disabledFlagStopsScan() {
        DeadlineReminderScheduler disabled = new DeadlineReminderScheduler(
                courseworkRepository, outboxEventRepository, properties(false));

        disabled.scanDueSoon();

        verifyNoInteractions(courseworkRepository);
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void noDueAssignmentsPublishesNothing() {
        when(courseworkRepository.listDueSoonAssignments(any(), any())).thenReturn(List.of());

        scheduler.scanDueSoon();

        verify(courseworkRepository, never()).listUnsubmitted(anyString());
        verifyNoInteractions(outboxEventRepository);
    }
}
