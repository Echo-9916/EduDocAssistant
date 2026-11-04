package com.cidas.edudoc.ai.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** AI 任务读写（SQL 委托 {@link AiTaskMapper}）。 */
@Repository
@RequiredArgsConstructor
public class AiTaskRepository {

    private final AiTaskMapper mapper;

    /** 幂等键插入：并发重复投递撞 uk 时回读既有行（EMBED 消息重投复用同一任务行）。 */
    public AiTask insertOrReuse(AiTask task) {
        try {
            mapper.insert(task);
            return task;
        } catch (DuplicateKeyException e) {
            AiTask existing = mapper.selectByIdempotencyKey(task.getIdempotencyKey());
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    public Optional<AiTask> findById(String idHex) {
        return Optional.ofNullable(mapper.selectById(idHex));
    }

    public Optional<AiTask> findActive(String versionIdHex, String taskType, String requestedByHex) {
        return Optional.ofNullable(mapper.selectActive(versionIdHex, taskType, requestedByHex));
    }

    public boolean casStart(String idHex) {
        return mapper.casStart(idHex) == 1;
    }

    /** 返回 false 表示任务已不在 RUNNING（被取消或已终态），worker 据此中止。 */
    public boolean updateProgress(String idHex, int progress) {
        return mapper.updateProgress(idHex, progress) == 1;
    }

    public boolean markSucceeded(String idHex) {
        return mapper.markSucceeded(idHex) == 1;
    }

    public void markFailed(String idHex, String errorCode) {
        mapper.markFailed(idHex, errorCode);
    }

    public boolean cancelQueued(String idHex) {
        return mapper.cancelQueued(idHex) == 1;
    }

    public boolean cancelRunning(String idHex) {
        return mapper.cancelRunning(idHex) == 1;
    }

    public void incrementRetry(String idHex) {
        mapper.incrementRetry(idHex);
    }
}
