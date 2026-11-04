package com.cidas.edudoc.document.application;

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

    record Preview(String documentId, String versionId, int versionNo, List<Block> blocks) {
    }

    Preview preview(String documentId, String versionId);
}
