package com.cidas.edudoc.ai.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 智能结果持久化对象（edudoc_ai_result）。 */
@Data
@AllArgsConstructor
public class AiResult {

    private String id;
    private String taskId;
    private String resultType;
    private String contentText;
    private String structuredJson;
    private String modelName;
    private String promptVersion;
    private Integer inputTokens;
    private Integer outputTokens;
}
