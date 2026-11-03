package com.cidas.edudoc.document.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-06 分块纯函数：段落边界、硬切、偏移连续性、heading 归属、token 估算。 */
class ChunkingServiceTest {

    private static TextBlock block(String text, String heading, int page) {
        return new TextBlock(text, heading, page);
    }

    @Test
    void mergesParagraphsUntilLimitAndSplitsAtBoundary() {
        List<TextBlock> blocks = List.of(
                block("a".repeat(60), "H1", 1),
                block("b".repeat(60), "H1 / H2", 1),
                block("c".repeat(30), "H1 / H2", 2));

        List<Chunk> chunks = new ChunkingService(100).chunk(blocks);

        assertEquals(2, chunks.size());
        assertEquals("a".repeat(60), chunks.get(0).text());
        assertEquals("b".repeat(60) + "\n" + "c".repeat(30), chunks.get(1).text());
        assertEquals(1, chunks.get(0).chunkNo());
        assertEquals(2, chunks.get(1).chunkNo());
        assertEquals("H1", chunks.get(0).headingPath());
        assertEquals("H1 / H2", chunks.get(1).headingPath());
        assertEquals(1, chunks.get(1).pageNo());
    }

    @Test
    void offsetsAreContinuousOverJoinedText() {
        List<TextBlock> blocks = List.of(block("hello", null, 0), block("world", null, 0));

        List<Chunk> chunks = new ChunkingService(11).chunk(blocks);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).charStart());
        assertEquals(11, chunks.get(0).charEnd());
        assertEquals("hello\nworld", chunks.get(0).text());
        assertNull(chunks.get(0).headingPath());
        assertNull(chunks.get(0).pageNo());
    }

    @Test
    void longParagraphIsHardSplitAndKeepsOffsets() {
        String text = "x".repeat(250);

        List<Chunk> chunks = new ChunkingService(100).chunk(List.of(block(text, "H", 1)));

        assertEquals(3, chunks.size());
        assertEquals(100, chunks.get(0).text().length());
        assertEquals(100, chunks.get(1).text().length());
        assertEquals(50, chunks.get(2).text().length());
        assertEquals(100, chunks.get(1).charStart());
        assertEquals(200, chunks.get(2).charStart());
        assertEquals(250, chunks.get(2).charEnd());
    }

    @Test
    void blankBlocksAreSkippedAndHashIsSha256Hex() {
        List<Chunk> chunks = new ChunkingService(100)
                .chunk(List.of(block("   ", "H", 1), block("real", "H", 1)));

        assertEquals(1, chunks.size());
        assertEquals("real", chunks.get(0).text());
        assertEquals(64, chunks.get(0).contentHash().length());
        assertTrue(chunks.get(0).contentHash().matches("[0-9a-f]{64}"));
        // token_count = ceil(非空白字符数 / 2)
        assertEquals(2, chunks.get(0).tokenCount());
    }

    @Test
    void emptyInputYieldsNoChunks() {
        assertTrue(new ChunkingService(100).chunk(List.of()).isEmpty());
    }
}
