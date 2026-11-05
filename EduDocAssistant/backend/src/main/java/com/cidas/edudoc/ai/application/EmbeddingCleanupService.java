package com.cidas.edudoc.ai.application;

/** 重解析清理端口：供 document 域同事务调用。旧向量行与 AI 引用行均为分块的外键子表，随旧块先行删除。 */
public interface EmbeddingCleanupService {

    int deleteByVersion(String versionIdHex);
}
