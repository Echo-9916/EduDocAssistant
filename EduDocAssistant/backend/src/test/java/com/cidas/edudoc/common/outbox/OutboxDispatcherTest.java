package com.cidas.edudoc.common.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B-05 调度器：PUBLISHED / 指数退避 / 超限 FAILED / 无路由 FAILED / 停用空转。对应详细设计 UT-20 发布侧。 */
class OutboxDispatcherTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);

    private OutboxDispatcher dispatcherWith(boolean enabled, int maxRetry) {
        when(redis.opsForStream()).thenReturn(streamOps);
        OutboxProperties properties = new OutboxProperties(enabled, 1000, 50, maxRetry,
                Map.of("DOCUMENT_PARSE_REQUESTED", "edudoc:stream:document-parse"));
        return new OutboxDispatcher(repository, redis, properties);
    }

    private static OutboxEvent event(int retryCount) {
        return new OutboxEvent("E1", "DOCUMENT_VERSION", "V1", "DOCUMENT_PARSE_REQUESTED",
                "{\"documentId\":\"D1\"}", retryCount, "NEW");
    }

    @Test
    void publishesAndMarksPublished() {
        OutboxDispatcher dispatcher = dispatcherWith(true, 5);
        when(repository.findPublishable(50)).thenReturn(List.of(event(0)));
        when(streamOps.add(anyString(), any(Map.class))).thenReturn(RecordId.of("1-0"));

        dispatcher.poll();

        verify(repository).markPublished("E1");
        verify(repository, never()).markRetry(anyString(), any(Long.class));
    }

    @Test
    void failureWithinMaxRetrySchedulesBackoff() {
        OutboxDispatcher dispatcher = dispatcherWith(true, 5);
        when(repository.findPublishable(50)).thenReturn(List.of(event(1)));
        when(streamOps.add(anyString(), any(Map.class))).thenThrow(new RuntimeException("redis down"));

        dispatcher.poll();

        verify(repository).markRetry("E1", 20);
        verify(repository, never()).markFailed(anyString());
    }

    @Test
    void failureBeyondMaxRetryMarksFailed() {
        OutboxDispatcher dispatcher = dispatcherWith(true, 5);
        when(repository.findPublishable(50)).thenReturn(List.of(event(4)));
        when(streamOps.add(anyString(), any(Map.class))).thenThrow(new RuntimeException("redis down"));

        dispatcher.poll();

        verify(repository).markFailed("E1");
    }

    @Test
    void unknownEventTypeMarkedFailedWithoutStream() {
        OutboxDispatcher dispatcher = dispatcherWith(true, 5);
        OutboxEvent unknown = new OutboxEvent("E2", "X", "Y", "SOMETHING_ELSE", "{}", 0, "NEW");
        when(repository.findPublishable(50)).thenReturn(List.of(unknown));

        dispatcher.poll();

        verify(repository).markFailed("E2");
        verify(streamOps, never()).add(anyString(), any(Map.class));
    }

    @Test
    void disabledSkipsPolling() {
        dispatcherWith(false, 5).poll();
        verify(repository, never()).findPublishable(eq(50));
    }

    @Test
    void backoffCappedAtTenMinutes() {
        OutboxDispatcher dispatcher = dispatcherWith(true, 99);
        when(repository.findPublishable(50)).thenReturn(List.of(event(20)));
        when(streamOps.add(anyString(), any(Map.class))).thenThrow(new RuntimeException("down"));

        dispatcher.poll();

        verify(repository).markRetry("E1", 600);
    }
}
