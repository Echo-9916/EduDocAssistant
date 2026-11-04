package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.application.EmbeddingCleanupService;
import com.cidas.edudoc.ai.application.EmbeddingTxService;
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

    @Override
    @Transactional
    public int deleteByVersion(String versionIdHex) {
        return embeddingRepository.deleteByVersion(versionIdHex);
    }
}
