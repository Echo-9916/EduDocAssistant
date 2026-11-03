package com.cidas.edudoc.document.domain;

/** 分块结果（edudoc_document_chunk 的领域表示，全文偏移定位）。 */
public record Chunk(int chunkNo, String headingPath, Integer pageNo, int charStart, int charEnd,
                    String text, int tokenCount, String contentHash) {
}
