package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.StructureNode;

import java.util.List;

/**
 * 预览结构树（C-08，§3.1.3「结构树」语义，D2 文本阅读视图数据源）。
 * blocks 按 chunk_no 升序一次全量返回；偏移为 C-01 密铺口径（闭区间、连接符归块）。
 * // ponytail: 超长文换分页（§4.2 20MB 上限内分块总量可控，先全量）
 */
public interface DocumentPreviewService {

    record Block(String chunkId, int chunkNo, String headingPath, Integer pageNo,
                 int charStart, int charEnd, String text) {
    }

    /**
     * structure：阅读结构层（与 blocks 同坐标系）；老版本无 structure_json 时为空数组。
     * vectorReady：当前版本分块是否全部完成向量化索引（R3「新版索引中」前端判据，1106 P2-1）。
     */
    record Preview(String documentId, String versionId, int versionNo, List<Block> blocks,
                   List<StructureNode> structure, boolean vectorReady) {
    }

    Preview preview(String documentId, String versionId);
}
