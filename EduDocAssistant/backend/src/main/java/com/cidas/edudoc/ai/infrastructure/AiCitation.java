package com.cidas.edudoc.ai.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 智能结果引用持久化对象（edudoc_ai_citation，page/char 由分块表回填）。 */
@Data
@AllArgsConstructor
public class AiCitation {

    private String id;
    private String resultId;
    private String chunkId;
    private Integer pageNo;
    private Integer charStart;
    private Integer charEnd;
    private String quoteText;
    private int rankNo;
}
