package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 冒烟抓出 ORA-02292：重解析必须先删 AI 引用行（fk_ai_citation_2 无级联），再删向量行。 */
class ReparseCleanupTest {

    @Test
    void deleteByVersionRemovesCitationsBeforeEmbeddings() {
        ChunkEmbeddingRepository embeddings = mock(ChunkEmbeddingRepository.class);
        AiResultRepository results = mock(AiResultRepository.class);
        when(results.deleteCitationsByVersion("V1")).thenReturn(3);
        when(embeddings.deleteByVersion("V1")).thenReturn(15);
        EmbeddingTxServiceImpl service =
                new EmbeddingTxServiceImpl(embeddings, mock(AiTaskRepository.class), results);

        int removed = service.deleteByVersion("V1");

        assertEquals(15, removed);
        InOrder order = inOrder(results, embeddings);
        order.verify(results).deleteCitationsByVersion("V1");
        order.verify(embeddings).deleteByVersion("V1");
    }
}
