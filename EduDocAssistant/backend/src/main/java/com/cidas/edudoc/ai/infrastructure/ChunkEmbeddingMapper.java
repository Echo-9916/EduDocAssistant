package com.cidas.edudoc.ai.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChunkEmbeddingMapper {

    int deleteByVersion(@Param("versionIdHex") String versionIdHex);

    List<ChunkEmbeddingRepository.PendingChunk> selectPendingChunks(@Param("versionIdHex") String versionIdHex);

    int upsertBatch(@Param("modelProfileIdHex") String modelProfileIdHex,
                    @Param("dimension") int dimension,
                    @Param("vectors") List<ChunkEmbeddingRepository.ChunkVector> vectors);

    int markChunksReady(@Param("chunkIdsHex") List<String> chunkIdsHex);
}
