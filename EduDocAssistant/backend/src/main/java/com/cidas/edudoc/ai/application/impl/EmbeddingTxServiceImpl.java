package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.application.EmbeddingCleanupService;
import com.cidas.edudoc.ai.application.EmbeddingTxService;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.ChunkVector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingTxServiceImpl implements EmbeddingTxService, EmbeddingCleanupService {

    private final ChunkEmbeddingRepository embeddingRepository;
    private final AiTaskRepository aiTaskRepository;
    private final AiResultRepository aiResultRepository;

    @Override
    @Transactional
    public void storeBatch(String taskIdHex, String modelProfileIdHex, int dimension,
                           List<ChunkVector> vectors, int progress) {
        if (dimension <= 0 || dimension > 4096) {
            throw new IllegalArgumentException("非法向量维度：" + dimension);
        }
        embeddingRepository.upsertReady(modelProfileIdHex, dimension, vectors);
        aiTaskRepository.updateProgress(taskIdHex, progress);
    }

    /** 重解析清理：引用行与向量行都是分块的外键子表，必须先行删除（冒烟抓出 ORA-02292）。 */
    @Override
    @Transactional
    public int deleteByVersion(String versionIdHex) {
        aiResultRepository.deleteCitationsByVersion(versionIdHex);
        return embeddingRepository.deleteByVersion(versionIdHex);
    }
}
