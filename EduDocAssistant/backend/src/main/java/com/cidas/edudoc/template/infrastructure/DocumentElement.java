package com.cidas.edudoc.template.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文档结构元素持久化对象（edudoc_document_element）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentElement {

    private String id;
    private String documentVersionId;
    private String elementKey;
    private String elementType;
    private String chapterNo;
    private Integer sequenceNo;
    private String displayLabel;
    private String captionText;
    private String locationJson;
}
