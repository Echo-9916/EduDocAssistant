package com.cidas.edudoc.common.api;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内幂等键去重。 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    // ponytail: 单机内存去重，重启即失；多实例或需持久化时换 Redis/DB
    private final Set<String> keys = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryAcquire(String key) {
        return keys.add(key);
    }
}
