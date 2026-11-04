package com.cidas.edudoc.document.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 段落边界切块（纯函数，C-01 修正口径）。全文 = 非空块文本以 \n 连接；块偏移为全文闭区间
 * [char_start, char_end]，块文本恒等于全文切片，块间严格密铺：首块 char_start=0、
 * 相邻块 char_end+1=后块 char_start，块间连接符归属后一块（其文本以 \n 起始）。
 * 空块不占偏移。硬切按 code point 计数（offsetByCodePoints），切点不落在代理对之间；
 * 偏移单位仍为 UTF-16 code unit（与前端 JS 字符串索引一致）。
 * token_count 为中文估算 ceil(非空白字符数/2)。
 */
public final class ChunkingService {

    private static final int MAX_HEADING_PATH = 1000;

    private final int maxChars;

    public ChunkingService(int maxChars) {
        this.maxChars = maxChars;
    }

    public List<Chunk> chunk(List<TextBlock> blocks) {
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String bufferHeading = null;
        int bufferPage = 0;
        int bufferStart = 0;
        int bufferNext = 0; // buffer 覆盖全文 [bufferStart, bufferNext)
        boolean firstBlock = true;
        int pos = 0;
        for (TextBlock block : blocks) {
            String text = block.text() == null ? "" : block.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            int blockStart = firstBlock ? pos : pos + 1;
            firstBlock = false;
            int localFrom = 0;
            for (String piece : hardSplit(text)) {
                int pieceStart = blockStart + localFrom;
                boolean leadingJoiner = localFrom == 0; // 本块首片，其前一位是块间连接符
                localFrom += piece.length();
                if (!buffer.isEmpty()) {
                    int junction = pieceStart - bufferNext; // 0=同块硬切续片，1=新块（连接符）
                    if (buffer.codePointCount(0, buffer.length()) + junction
                            + piece.codePointCount(0, piece.length()) > maxChars) {
                        chunks.add(flush(buffer, bufferHeading, bufferPage, bufferStart, chunks.size() + 1));
                        buffer.setLength(0);
                    } else if (junction == 1) {
                        buffer.append('\n');
                    }
                }
                if (buffer.isEmpty()) {
                    if (leadingJoiner && pos != 0) {
                        buffer.append('\n');
                        bufferStart = pieceStart - 1;
                    } else {
                        bufferStart = pieceStart;
                    }
                    bufferHeading = blankToNull(block.headingPath());
                    bufferPage = block.pageNo();
                }
                buffer.append(piece);
                bufferNext = pieceStart + piece.length();
            }
            pos = blockStart + text.length();
        }
        if (!buffer.isEmpty()) {
            chunks.add(flush(buffer, bufferHeading, bufferPage, bufferStart, chunks.size() + 1));
        }
        return chunks;
    }

    private Chunk flush(StringBuilder buffer, String headingPath, int pageNo, int charStart, int chunkNo) {
        String content = buffer.toString();
        int chars = content.replaceAll("\\s+", "").length();
        return new Chunk(chunkNo, truncate(headingPath), pageNo == 0 ? null : pageNo,
                charStart, charStart + content.length() - 1, content,
                (int) Math.ceil(chars / 2.0), sha256Hex(content));
    }

    /** 按 code point 硬切至 maxChars，代理对不得切断。 */
    private List<String> hardSplit(String text) {
        List<String> pieces = new ArrayList<>();
        for (int from = 0; from < text.length(); ) {
            int codePoints = Math.min(maxChars, text.codePointCount(from, text.length()));
            int to = text.offsetByCodePoints(from, codePoints);
            pieces.add(text.substring(from, to));
            from = to;
        }
        return pieces;
    }

    private static String truncate(String headingPath) {
        if (headingPath == null) {
            return null;
        }
        return headingPath.length() > MAX_HEADING_PATH ? headingPath.substring(0, MAX_HEADING_PATH) : headingPath;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}