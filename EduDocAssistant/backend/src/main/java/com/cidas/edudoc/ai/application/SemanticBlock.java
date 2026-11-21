package com.cidas.edudoc.ai.application;

/**
 * 智能处理语义块：与文档块编辑同一套结构节点（一级/二级标题、正文、代码块等）。
 * blockId 只在本版本内有效；chunkId 仍指向真实分块，用于引用表落库。
 */
public record SemanticBlock(String blockId, String chunkId, int chunkNo, Integer pageNo,
                            int start, int end, String kind, Integer level, String text, int order) {
}
