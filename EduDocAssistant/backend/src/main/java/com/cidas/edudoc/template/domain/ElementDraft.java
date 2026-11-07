package com.cidas.edudoc.template.domain;

/** 结构树解析草稿（未落库形态，字段对齐 edudoc_document_element）。 */
public record ElementDraft(String elementKey, String elementType, String chapterNo, Integer sequenceNo,
                           String displayLabel, String captionText, String locationJson) {
}
