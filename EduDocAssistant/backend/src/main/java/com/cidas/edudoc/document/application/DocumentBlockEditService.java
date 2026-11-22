package com.cidas.edudoc.document.application;

import java.util.List;

/** 块编辑：H1/H2/正文/代码块/图片整篇保存，生成新的 EDIT 文本版本（不改原文件）。 */
public interface DocumentBlockEditService {

    Result save(String documentId, String baseVersionId, List<Block> blocks);

    /** imageId 仅 IMAGE 块使用，指向已上传的 edudoc_document_image。 */
    record Block(String type, String text, String lang, String imageId) {
    }

    record Result(String versionId, int versionNo) {
    }
}