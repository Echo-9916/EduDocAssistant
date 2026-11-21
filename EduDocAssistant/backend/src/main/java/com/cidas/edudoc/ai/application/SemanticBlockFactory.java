package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.document.application.StructureJson;
import com.cidas.edudoc.document.domain.StructureNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 版本结构节点 + 分块全文 → 智能处理语义块；无结构层时回退为分块，保证老文档可用。 */
@Component
public class SemanticBlockFactory {

    public List<SemanticBlock> from(String structureJson, List<VersionChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        StringBuilder fullBuilder = new StringBuilder();
        chunks.forEach(c -> fullBuilder.append(c.contentText() == null ? "" : c.contentText()));
        String full = fullBuilder.toString();
        List<SemanticBlock> blocks = new ArrayList<>();
        StructureJson.read(structureJson).stream()
                .filter(n -> n.start() != null && n.end() != null)
                .sorted(Comparator.comparingInt(StructureNode::start).thenComparingInt(StructureNode::no))
                .forEach(node -> {
                    int start = Math.max(0, Math.min(node.start(), full.length()));
                    int end = Math.max(start - 1, Math.min(node.end(), full.length() - 1));
                    String text = end < start ? "" : full.substring(start, end + 1);
                    if (text.isBlank()) {
                        return;
                    }
                    VersionChunk chunk = containing(chunks, start);
                    if (chunk == null) {
                        return;
                    }
                    blocks.add(new SemanticBlock("B" + node.no(), chunk.chunkId(), chunk.chunkNo(),
                            node.pageNo() == null ? chunk.pageNo() : node.pageNo(),
                            start, end, node.kind(), node.level(), text, node.no()));
                });
        if (!blocks.isEmpty()) {
            return blocks;
        }
        // 老版本没有 structure_json：按分块作为单位，保持现有行为
        for (VersionChunk chunk : chunks) {
            String text = chunk.contentText() == null ? "" : chunk.contentText();
            if (text.isBlank()) {
                continue;
            }
            int start = chunk.charStart() == null ? 0 : chunk.charStart();
            int end = chunk.charEnd() == null ? start + text.length() - 1 : chunk.charEnd();
            blocks.add(new SemanticBlock(chunk.chunkId(), chunk.chunkId(), chunk.chunkNo(), chunk.pageNo(),
                    start, end, "CHUNK", null, text, chunk.chunkNo()));
        }
        return blocks;
    }

    private static VersionChunk containing(List<VersionChunk> chunks, int start) {
        for (VersionChunk chunk : chunks) {
            if (chunk.charStart() != null && chunk.charEnd() != null
                    && chunk.charStart() <= start && chunk.charEnd() >= start) {
                return chunk;
            }
        }
        return chunks.isEmpty() ? null : chunks.get(0);
    }
}
