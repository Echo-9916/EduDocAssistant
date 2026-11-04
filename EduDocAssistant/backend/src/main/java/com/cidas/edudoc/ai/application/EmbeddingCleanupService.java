package com.cidas.edudoc.ai.application;

/** 向量清理端口：供 document 域重解析同事务调用（旧向量行随旧块删除，FK 无级联）。 */
public interface EmbeddingCleanupService {

    int deleteByVersion(String versionIdHex);
}
