package com.cidas.edudoc.document.domain;

import java.util.List;

/** 一次切块的产物：chunks 保持 C-01 密铺口径；nodes 是同一坐标系上的阅读结构层。 */
public record ChunkingResult(List<Chunk> chunks, List<StructureNode> nodes) {
}