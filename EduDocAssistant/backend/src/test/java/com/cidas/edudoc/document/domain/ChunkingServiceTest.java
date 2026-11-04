package com.cidas.edudoc.document.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-06 分块纯函数（C-01 修正口径）：段落边界、code-point 安全硬切、偏移文档级不变量、
 * heading 归属、token 估算。char_start/char_end 为全文（非空块文本以 \n 连接）闭区间偏移，
 * 块间连接符归属后一块（下一块 char_start 含前导连接符位）。
 */
class ChunkingServiceTest {

    private static TextBlock block(String text, String heading, int page) {
        return new TextBlock(text, heading, page);
    }

    /** 分块视角下的全文：非空块 strip 后以 \n 连接。 */
    private static String joinedText(List<TextBlock> blocks) {
        List<String> texts = new ArrayList<>();
        for (TextBlock b : blocks) {
            String t = b.text() == null ? "" : b.text().strip();
            if (!t.isEmpty()) {
                texts.add(t);
            }
        }
        return String.join("\n", texts);
    }

    /** 块内不得有孤立代理项：每个高代理项后必须紧跟低代理项，且不得出现游离低代理项。 */
    private static void assertNoLoneSurrogates(Chunk c) {
        String s = c.text();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                assertTrue(i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)),
                        "高代理项后缺低代理项: chunkNo=" + c.chunkNo() + " at=" + i);
                i++;
            } else {
                assertTrue(!Character.isLowSurrogate(ch), "游离低代理项: chunkNo=" + c.chunkNo() + " at=" + i);
            }
        }
    }

    /** 文档级不变量：首块 char_start=0、相邻块 char_end+1=后块 char_start、块文本=全文闭区间切片、末块覆盖到文尾。 */
    private static void assertTilingInvariants(List<TextBlock> blocks, List<Chunk> chunks, int maxChars) {
        String full = joinedText(blocks);
        assertEquals(0, chunks.get(0).charStart(), "首块 char_start 必须为 0");
        int prevEnd = -1;
        for (Chunk c : chunks) {
            assertEquals(prevEnd + 1, c.charStart(), "块间必须连续（连接符归后块）: chunkNo=" + c.chunkNo());
            assertEquals(full.substring(c.charStart(), c.charEnd() + 1), c.text(),
                    "块文本必须等于全文切片: chunkNo=" + c.chunkNo());
            assertTrue(c.text().codePointCount(0, c.text().length()) <= maxChars + 1,
                    "单块长度不超上限+连接符: chunkNo=" + c.chunkNo());
            prevEnd = c.charEnd();
        }
        assertEquals(full.length() - 1, prevEnd, "末块必须覆盖到文尾");
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
        assertEquals(0, chunks.get(0).charStart());
        assertEquals(59, chunks.get(0).charEnd());
        // 边界落在块衔接处：连接符归后块，块文本以 \n 开头
        assertEquals("\n" + "b".repeat(60) + "\n" + "c".repeat(30), chunks.get(1).text());
        assertEquals(60, chunks.get(1).charStart());
        assertEquals(151, chunks.get(1).charEnd());
        assertEquals(1, chunks.get(0).chunkNo());
        assertEquals(2, chunks.get(1).chunkNo());
        assertEquals("H1", chunks.get(0).headingPath());
        assertEquals("H1 / H2", chunks.get(1).headingPath());
        assertEquals(1, chunks.get(1).pageNo());
        assertTilingInvariants(blocks, chunks, 100);
    }

    @Test
    void offsetsAreContinuousOverJoinedText() {
        List<TextBlock> blocks = List.of(block("hello", null, 0), block("world", null, 0));

        List<Chunk> chunks = new ChunkingService(11).chunk(blocks);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).charStart());
        assertEquals(10, chunks.get(0).charEnd());
        assertEquals("hello\nworld", chunks.get(0).text());
        assertNull(chunks.get(0).headingPath());
        assertNull(chunks.get(0).pageNo());
        assertTilingInvariants(blocks, chunks, 11);
    }

    @Test
    void longParagraphIsHardSplitAndKeepsOffsets() {
        String text = "x".repeat(250);
        List<TextBlock> blocks = List.of(block(text, "H", 1));

        List<Chunk> chunks = new ChunkingService(100).chunk(blocks);

        assertEquals(3, chunks.size());
        assertEquals(100, chunks.get(0).text().length());
        assertEquals(100, chunks.get(1).text().length());
        assertEquals(50, chunks.get(2).text().length());
        assertEquals(100, chunks.get(1).charStart());
        assertEquals(199, chunks.get(1).charEnd());
        assertEquals(200, chunks.get(2).charStart());
        assertEquals(249, chunks.get(2).charEnd());
        assertTilingInvariants(blocks, chunks, 100);
    }

    @Test
    void hardSplitNeverCutsSurrogatePair() {
        // 每个 emoji / 生僻字为 2 个 UTF-16 code unit；按 code point 切分，切点不得落在代理对之间
        String text = "😀".repeat(80) + "𠮷".repeat(40);
        List<TextBlock> blocks = List.of(block(text, null, 1));

        List<Chunk> chunks = new ChunkingService(100).chunk(blocks);

        assertEquals(2, chunks.size());
        chunks.forEach(ChunkingServiceTest::assertNoLoneSurrogates);
        assertEquals(text, chunks.get(0).text() + chunks.get(1).text());
        assertTilingInvariants(blocks, chunks, 100);
    }

    @Test
    void surrogateTextSurvivesMidBlockBoundaries() {
        // 300 个 BMP 字符后接代理对，切点恰落在代理对之前/之后的各种位置
        String text = "a".repeat(199) + "😀𠮷" + "b".repeat(100);
        List<TextBlock> blocks = List.of(block(text, "H", 2));

        List<Chunk> chunks = new ChunkingService(100).chunk(blocks);

        chunks.forEach(ChunkingServiceTest::assertNoLoneSurrogates);
        assertTilingInvariants(blocks, chunks, 100);
    }

    @Test
    void blankBlocksAreSkippedAndHashIsSha256Hex() {
        List<TextBlock> blocks = List.of(block("   ", "H", 1), block("real", "H", 1));

        List<Chunk> chunks = new ChunkingService(100).chunk(blocks);

        assertEquals(1, chunks.size());
        assertEquals("real", chunks.get(0).text());
        assertEquals(0, chunks.get(0).charStart(), "空块不占全文偏移");
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
