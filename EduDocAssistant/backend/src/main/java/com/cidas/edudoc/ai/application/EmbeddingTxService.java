package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.ChunkVector;

import java.util.List;

/** 向量化落库事务端口：单批 upsert + 块 READY + 任务进度，单事务提交。 */
public interface EmbeddingTxService {

    void storeBatch(String taskIdHex, String modelProfileIdHex, int dimension,
                    List<ChunkVector> vectors, int progress);
}
