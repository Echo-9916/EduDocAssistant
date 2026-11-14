package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 分块批量读写（SQL 委托给 {@link DocumentChunkMapper}）。 */
@Repository
@RequiredArgsConstructor
public class DocumentChunkRepository {

    private static final int BATCH_SIZE = 50;

    private final DocumentChunkMapper mapper;

    public void insertAll(List<DocumentChunk> chunks) {
        for (int from = 0; from < chunks.size(); from += BATCH_SIZE) {
            mapper.insertBatch(chunks.subList(from, Math.min(chunks.size(), from + BATCH_SIZE)));
        }
    }

    public void deleteByVersion(String versionIdHex) {
        mapper.deleteByVersion(versionIdHex);
    }

    public int countByVersion(String versionIdHex) {
        return mapper.countByVersion(versionIdHex);
    }

    /** 版本全部分块（chunk_no 升序）：预览结构树数据源。 */
    public List<DocumentChunk> selectAllByVersion(String versionIdHex) {
        return mapper.selectAllByVersion(versionIdHex);
    }

    /**
     * 版本恢复：把源版本分块复制到新版本，index_status 重置为 PENDING（新 chunk 没有向量）。
     * 调用方负责发布 DOCUMENT_EMBED_REQUESTED，让新版本重新索引。
     */
    public void copyAsPending(String sourceVersionIdHex, String newVersionIdHex) {
        List<DocumentChunk> copies = mapper.selectAllByVersion(sourceVersionIdHex).stream()
                .map(c -> new DocumentChunk(Ids.hex16(), newVersionIdHex, c.getChunkNo(), c.getHeadingPath(),
                        c.getPageNo(), c.getCharStart(), c.getCharEnd(), c.getContentText(),
                        c.getTokenCount(), c.getContentHash(), "PENDING"))
                .toList();
        insertAll(copies);
    }
}
