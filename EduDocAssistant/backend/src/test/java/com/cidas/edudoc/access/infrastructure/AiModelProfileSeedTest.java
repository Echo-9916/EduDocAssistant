package com.cidas.edudoc.access.infrastructure;

import com.cidas.edudoc.ai.infrastructure.AiModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C-02 模型档案 seed 幂等：三条 profile 缺失时插入、已存在时跳过。 */
class AiModelProfileSeedTest {

    private static AiModelProperties models() {
        return new AiModelProperties(
                new AiModelProperties.Llm("Pro/moonshotai/Kimi-K2-Instruct", 0.3),
                new AiModelProperties.Embedding("BAAI/bge-m3", 1024),
                new AiModelProperties.Rerank("BAAI/bge-reranker-v2-m3"));
    }

    @Test
    void seedsThreeProfilesWhenAbsent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);

        new SeedDataRunner(jdbc, mock(PasswordEncoder.class), models()).seedAiModelProfiles();

        verify(jdbc).update(startsWith("INSERT INTO edudoc_ai_model_profile"),
                eq("LLM_MAIN"), eq("LLM"), eq("Pro/moonshotai/Kimi-K2-Instruct"),
                eq("edudoc.ai.llm.base-url"), isNull(), isNull(), contains("temperature"));
        verify(jdbc).update(startsWith("INSERT INTO edudoc_ai_model_profile"),
                eq("EMB_BGE_M3"), eq("EMBEDDING"), eq("BAAI/bge-m3"),
                eq("edudoc.ai.embedding.base-url"), eq(1024), eq("COSINE"), any());
        verify(jdbc).update(startsWith("INSERT INTO edudoc_ai_model_profile"),
                eq("RERANK_BGE_V2"), eq("RERANK"), eq("BAAI/bge-reranker-v2-m3"),
                eq("edudoc.ai.rerank.base-url"), isNull(), isNull(), isNull());
    }

    @Test
    void skipsExistingProfiles() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);

        new SeedDataRunner(jdbc, mock(PasswordEncoder.class), models()).seedAiModelProfiles();

        verify(jdbc, never()).update(startsWith("INSERT INTO edudoc_ai_model_profile"), any(Object[].class));
    }

    private static String contains(String fragment) {
        return org.mockito.ArgumentMatchers.argThat(value -> value != null && value.contains(fragment));
    }
}
