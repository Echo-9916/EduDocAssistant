package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.Chunk;

import java.util.List;

/** 解析结果落库事务边界：删旧插新分块 + 文档状态 + 审计，单事务。 */
public interface DocumentParseTxService {

    void writeSuccess(String documentId, String versionId, List<Chunk> chunks);

    void writeFailure(String documentId, String versionId, String reason);
}
