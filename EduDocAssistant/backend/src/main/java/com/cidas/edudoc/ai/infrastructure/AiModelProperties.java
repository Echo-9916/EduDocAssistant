package com.cidas.edudoc.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 模型档案注入配置（edudoc.ai.*）：模型名经环境变量可覆盖，落 edudoc_ai_model_profile。 */
@ConfigurationProperties("edudoc.ai")
public record AiModelProperties(Llm llm, Embedding embedding, Rerank rerank) {

    public record Llm(String model, double temperature) {
    }

    public record Embedding(String model, int dimension) {
    }

    public record Rerank(String model) {
    }
}
