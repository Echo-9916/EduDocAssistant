package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.Chunk;
import com.cidas.edudoc.document.domain.StructureNode;

import java.util.List;

/** 解析结果落库事务边界：删旧插新分块 + 结构层 + 文档状态 + 审计，单事务。 */
public interface DocumentParseTxService {

    /** 返回 false 表示文档已不在 PARSING、整笔跳过（1103 复审 N4：调用方据此区分日志，不谎报成功）。 */
    boolean writeSuccess(String documentId, String versionId, List<Chunk> chunks, List<StructureNode> nodes);

    boolean writeFailure(String documentId, String versionId, String reason);
}
