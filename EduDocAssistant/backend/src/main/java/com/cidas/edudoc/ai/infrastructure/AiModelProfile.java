package com.cidas.edudoc.ai.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 模型档案持久化对象（edudoc_ai_model_profile，只读视图）。 */
@Data
@AllArgsConstructor
public class AiModelProfile {

    private String id;
    private String profileCode;
    private String providerType;
    private String modelName;
    private Integer embeddingDimension;
    private String metricType;
    private String status;
}
