package com.cidas.edudoc.message.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 信箱仓储：dedup_key 撞 uk_notification_1 的幂等翻译与已读更新的本人圈定参数。 */
class NotificationRepositoryTest {

    private static final String HEX_USER = "A".repeat(32);
    private static final String HEX_MSG = "C".repeat(32);

    private final NotificationMapper mapper = mock(NotificationMapper.class);
    private final NotificationRepository repository = new NotificationRepository(mapper);

    private boolean insert() {
        return repository.insertIfAbsent(HEX_USER, "SUBMISSION", "新提交", null,
                null, null, null, "SUBMISSION:asg1:" + HEX_USER);
    }

    @Test
    void duplicateDedupKeyReturnsFalseAndDoesNotInsertSecondRow() {
        when(mapper.insertNotification(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_notification_1"));

        assertFalse(insert());
        // 撞唯一键即「已存在」：只发过一条 INSERT，不重试、不写第二行、不冒异常
        verify(mapper, times(1)).insertNotification(anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyString());
    }

    @Test
    void freshInsertReturnsTrue() {
        when(mapper.insertNotification(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyString()))
                .thenReturn(1);

        assertTrue(insert());
    }

    @Test
    void updateReadScopesToCurrentUser() {
        when(mapper.updateRead(anyString(), anyList(), anyBoolean())).thenReturn(1);

        assertEquals(1, repository.updateRead(HEX_USER, List.of(HEX_MSG), true));
        // 越权静默忽略的实现点：recipient_id 一律传当前用户，SQL 圈定后别人的 id 不命中
        verify(mapper).updateRead(HEX_USER, List.of(HEX_MSG), true);
    }
}
