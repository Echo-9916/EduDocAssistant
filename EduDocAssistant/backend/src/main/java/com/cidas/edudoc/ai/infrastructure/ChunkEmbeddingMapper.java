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

    /** 版本全部分块（chunk_no 升序，含状态），生成与问答链路的数据源。 */
    java.util.List<ChunkEmbeddingRepository.VersionChunk> selectVersionChunks(
            @Param("versionIdHex") String versionIdHex);

    /** 未就绪（非 READY）分块数：编排入口的向量就绪判定。 */
    int countNotReady(@Param("versionIdHex") String versionIdHex);
}
