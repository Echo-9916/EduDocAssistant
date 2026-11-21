package com.cidas.edudoc.document.application;

import java.util.List;

/** 块编辑：H1/H2/正文/代码块整篇保存，生成新的 EDIT 文本版本（不改原文件）。 */
public interface DocumentBlockEditService {

    Result save(String documentId, String baseVersionId, List<Block> blocks);

    record Block(String type, String text, String lang) {
    }

    record Result(String versionId, int versionNo) {
    }
}