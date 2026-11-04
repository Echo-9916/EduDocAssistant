package com.cidas.edudoc.ai.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 分块向量读写（edudoc_chunk_embedding，VECTOR 列以 TO_VECTOR 字符串绑定）。 */
@Repository
@RequiredArgsConstructor
public class ChunkEmbeddingRepository {

    private final ChunkEmbeddingMapper mapper;

    /** 重解析清理：旧向量行随旧块删除（fk_chunk_embedding_1 无级联，先删向量再删块）。 */
    public int deleteByVersion(String versionIdHex) {
        return mapper.deleteByVersion(versionIdHex);
    }

    public List<PendingChunk> selectPendingChunks(String versionIdHex) {
        return mapper.selectPendingChunks(versionIdHex);
    }

    /** 按 (chunk_id, model_profile_id) uk 覆盖写（消息重投幂等）并置块 READY，单批同调用方事务。 */
    public void upsertReady(String modelProfileIdHex, int dimension, List<ChunkVector> vectors) {
        mapper.upsertBatch(modelProfileIdHex, dimension, vectors);
        mapper.markChunksReady(vectors.stream().map(ChunkVector::chunkIdHex).toList());
    }

    /** 待嵌入分块（chunk_no 升序）。 */
    public record PendingChunk(String chunkId, int chunkNo, String contentText) {
    }

    /** 块 + 向量字符串（[a,b,…] 形态，TO_VECTOR 绑定用）。 */
    public record ChunkVector(String chunkIdHex, String embeddingText) {
    }

    /** 版本分块读视图（生成/检索/预览共用）。 */
    public record VersionChunk(String chunkId, int chunkNo, Integer pageNo,
                               Integer charStart, Integer charEnd, String contentText, String indexStatus) {
    }

    public List<VersionChunk> selectVersionChunks(String versionIdHex) {
        return mapper.selectVersionChunks(versionIdHex);
    }

    /** 检索命中（向量距离升序）。 */
    public record SearchHit(String chunkId, String contentText, Integer pageNo,
                            Integer charStart, Integer charEnd, double distance) {
    }

    public List<SearchHit> searchByVersion(String versionIdHex, String profileIdHex,
                                           String vectorText, int dimension, int topK) {
        if (dimension <= 0 || dimension > 4096 || topK <= 0 || topK > 50) {
            throw new IllegalArgumentException("非法检索参数：dimension=" + dimension + " topK=" + topK);
        }
        return mapper.searchByVersion(versionIdHex, profileIdHex, vectorText, dimension, topK);
    }

    public int countNotReady(String versionIdHex) {
        return mapper.countNotReady(versionIdHex);
    }
}
