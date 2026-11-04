package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.common.persistence.Ids;
import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 引用合法性与回填（C-04/C-06）：模型给出的 chunkId 必须属于本版本，非法引用直接丢弃；
 * page_no/char_start/char_end 由分块表回填，quote_text 取分块文本截断——模型不得引用不存在的块。
 */
@Component
public class CitationService {

    public Map<String, VersionChunk> indexByVersion(List<VersionChunk> chunks) {
        return chunks.stream().collect(Collectors.toMap(VersionChunk::chunkId, Function.identity()));
    }

    public List<AiCitation> resolve(String resultId, Map<String, VersionChunk> validChunks,
                                    List<String> chunkIds) {
        List<AiCitation> citations = new ArrayList<>();
        int rank = 1;
        for (String chunkId : chunkIds) {
            VersionChunk chunk = chunkId == null ? null : validChunks.get(chunkId);
            if (chunk == null) {
                continue; // 非法引用丢弃（不属于本版本分块即非法）
            }
            citations.add(new AiCitation(Ids.hex16(), resultId, chunk.chunkId(), chunk.pageNo(),
                    chunk.charStart(), chunk.charEnd(),
                    AiResultRepository.clipQuote(chunk.contentText()), rank++));
        }
        return citations;
    }
}
