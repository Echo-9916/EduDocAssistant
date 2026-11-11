package com.cidas.edudoc.message.application;

import com.cidas.edudoc.message.application.InboxService.DeliverCommand;
import com.cidas.edudoc.message.infrastructure.NotificationWorkerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2-4 信箱投递 worker：一条事件多收件人逐人 deliver、去重键形状（§4.3 适配：
 * {notifType}:{aggregateRef}[:{dedupDate}]，收件人维度由 uk_notification_1 覆盖）、
 * 重放/偏好关闭走 deliver 的 false 分支不产生第二封信、单人失败不拖累其余人。
 */
class NotificationWorkerTest {

    private static final String REF = "A".repeat(32);
    private static final String R1 = "B".repeat(32);
    private static final String R2 = "C".repeat(32);
    private static final String ACTOR = "D".repeat(32);

    private final InboxService inboxService = mock(InboxService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationWorker worker = new NotificationWorker(
            mock(StringRedisTemplate.class), inboxService,
            new NotificationWorkerProperties(false, 1, "edudoc:stream:notification", "cg-notification"),
            objectMapper);

    /** 载荷用生产者同款 NotificationEvents.payload 装配：顺带锁住两侧字段名契约。 */
    private String payload(String notifType, String dedupDate, List<String> recipients) throws Exception {
        return objectMapper.writeValueAsString(NotificationEvents.payload(notifType, REF, dedupDate,
                "ASSIGNMENT", REF, ACTOR, recipients, "作业提交通知", "学生 张三 已提交《实验一》（第 2 次）"));
    }

    @Test
    void oneEventDeliversPerRecipientWithShapedDedupKey() throws Exception {
        when(inboxService.deliver(any())).thenReturn(true);

        worker.process(payload("SUBMISSION", null, List.of(R1, R2)));

        ArgumentCaptor<DeliverCommand> captor = ArgumentCaptor.forClass(DeliverCommand.class);
        verify(inboxService, times(2)).deliver(captor.capture());
        List<DeliverCommand> calls = captor.getAllValues();
        assertEquals(List.of(R1, R2), calls.stream().map(DeliverCommand::recipientId).toList());
        for (DeliverCommand cmd : calls) {
            // 去重键形状：{notifType}:{aggregateRef}，不含日期段
            assertEquals("SUBMISSION:" + REF, cmd.dedupKey());
            assertEquals("SUBMISSION", cmd.type());
            assertEquals("作业提交通知", cmd.title());
            assertTrue(cmd.body().contains("实验一"));
            assertEquals("ASSIGNMENT", cmd.resourceType());
            assertEquals(REF, cmd.resourceRef());
            assertEquals(ACTOR, cmd.actorId());
        }
    }

    @Test
    void dueScanKeyCarriesScanDate() throws Exception {
        when(inboxService.deliver(any())).thenReturn(true);

        worker.process(payload("SUBMISSION", "20261110", List.of(R1)));

        ArgumentCaptor<DeliverCommand> captor = ArgumentCaptor.forClass(DeliverCommand.class);
        verify(inboxService).deliver(captor.capture());
        assertEquals("SUBMISSION:" + REF + ":20261110", captor.getValue().dedupKey());
    }

    @Test
    void preferenceOffGoesThroughDeliverFalseBranchOncePerRecipient() throws Exception {
        // 偏好判断在 deliver 内部：worker 不预判，对每个收件人照常调用一次；false 不算失败
        when(inboxService.deliver(any())).thenAnswer(invocation -> {
            DeliverCommand cmd = invocation.getArgument(0);
            return !R1.equals(cmd.recipientId()); // R1 关掉了 SUBMISSION 偏好
        });

        worker.process(payload("SUBMISSION", null, List.of(R1, R2)));

        ArgumentCaptor<DeliverCommand> captor = ArgumentCaptor.forClass(DeliverCommand.class);
        verify(inboxService, times(2)).deliver(captor.capture());
        assertEquals(List.of(R1, R2), captor.getAllValues().stream().map(DeliverCommand::recipientId).toList());
    }

    @Test
    void replayedEventDoesNotProduceSecondLetter() throws Exception {
        // 幂等：同一事件重放 → 每人都再走一次 deliver，但撞 uk_notification_1 全部返回 false，不写第二行
        when(inboxService.deliver(any())).thenReturn(false);
        String event = payload("FEEDBACK", null, List.of(R1, R2));

        worker.process(event);
        worker.process(event);

        ArgumentCaptor<DeliverCommand> captor = ArgumentCaptor.forClass(DeliverCommand.class);
        verify(inboxService, times(4)).deliver(captor.capture());
        List<String> keys = captor.getAllValues().stream().map(DeliverCommand::dedupKey).distinct().toList();
        // 同一收件人两轮拿到完全相同的去重键 → 唯一约束兜底「重放即原结果」
        assertEquals(List.of("FEEDBACK:" + REF), keys);
    }

    @Test
    void failingRecipientDoesNotStopOthers() throws Exception {
        when(inboxService.deliver(any())).thenAnswer(invocation -> {
            DeliverCommand cmd = invocation.getArgument(0);
            if (R1.equals(cmd.recipientId())) {
                throw new IllegalStateException("库连接抖动");
            }
            return true;
        });

        worker.process(payload("SUBMISSION", null, List.of(R1, R2)));

        ArgumentCaptor<DeliverCommand> captor = ArgumentCaptor.forClass(DeliverCommand.class);
        verify(inboxService, times(2)).deliver(captor.capture());
        assertEquals(R2, captor.getAllValues().get(1).recipientId());
    }

    @Test
    void malformedPayloadIsDiscardedWithoutDeliver() throws Exception {
        // 缺 recipientIds：载荷损坏重试无意义，丢弃且不调 deliver
        worker.process("{\"notifType\":\"SUBMISSION\",\"aggregateRef\":\"" + REF + "\"}");
        verify(inboxService, never()).deliver(any());

        // 非法 JSON：process 抛出，由 handleRecord 记全栈后 ACK（不吞栈、不重投）
        assertThrows(Exception.class, () -> worker.process("not-json"));
        verify(inboxService, never()).deliver(any());
    }
}
