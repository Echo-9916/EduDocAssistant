package com.cidas.edudoc.document.infrastructure;

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
}
