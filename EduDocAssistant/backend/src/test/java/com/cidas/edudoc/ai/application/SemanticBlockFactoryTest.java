package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.document.application.StructureJson;
import com.cidas.edudoc.document.domain.StructureNode;
import com.cidas.edudoc.document.domain.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 智能处理单位回归：一个标题/一个正文段各一块，引用落到所属 chunk。 */
class SemanticBlockFactoryTest {

    private final SemanticBlockFactory factory = new SemanticBlockFactory();

    @Test
    void splitsStructureNodesIntoBlocksAndKeepsOwningChunk() {
        List<VersionChunk> chunks = List.of(
                new VersionChunk("C1", 1, 1, 0, 5, "标题\n正文A", "READY"),
                new VersionChunk("C2", 2, 1, 6, 8, "正文B", "READY"));
        String structure = StructureJson.write(List.of(
                StructureNode.ranged(1, TextBlock.HEADING, 0, 1, null, 1, 1, null, null, null, null),
                StructureNode.ranged(2, TextBlock.PARAGRAPH, 3, 5, null, 1, null, null, null, null, null),
                StructureNode.ranged(3, TextBlock.PARAGRAPH, 6, 8, null, 1, null, null, null, null, null)));

        List<SemanticBlock> blocks = factory.from(structure, chunks);

        assertEquals(List.of("B1", "B2", "B3"), blocks.stream().map(SemanticBlock::blockId).toList());
        assertEquals(List.of("标题", "正文A", "正文B"), blocks.stream().map(SemanticBlock::text).toList());
        assertEquals(List.of("C1", "C1", "C2"), blocks.stream().map(SemanticBlock::chunkId).toList());
        assertEquals("HEADING", blocks.get(0).kind());
        assertEquals("PARAGRAPH", blocks.get(1).kind());
    }

    @Test
    void fallsBackToChunksWhenStructureMissing() {
        List<VersionChunk> chunks = List.of(
                new VersionChunk("C1", 1, 1, 0, 4, "hello", "READY"));

        List<SemanticBlock> blocks = factory.from(null, chunks);

        assertEquals(1, blocks.size());
        assertEquals("C1", blocks.get(0).blockId());
        assertEquals("CHUNK", blocks.get(0).kind());
        assertEquals("hello", blocks.get(0).text());
    }
}
