package com.cidas.edudoc.common.api;

/** 幂等键存储端口：写操作按 Idempotency-Key 去重。 */
public interface IdempotencyStore {

    /** 首次见到该 key 返回 true；重复提交返回 false。 */
    boolean tryAcquire(String key);
}
