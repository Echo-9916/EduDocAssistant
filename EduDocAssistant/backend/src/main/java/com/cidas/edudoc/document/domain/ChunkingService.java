package com.cidas.edudoc.document.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 段落边界切块（纯函数）：块文本以 \n 连接视为全文，char_start/char_end 为全文偏移；
 * 累积至 maxChars 在段落边界切开，单段超限硬切；token_count 为中文估算 ceil(chars/2)。
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
        int bufferCharStart = 0;
        int bufferCharEnd = 0;
        int blockStart = 0;
        for (TextBlock block : blocks) {
            String text = block.text() == null ? "" : block.text().strip();
            int localFrom = 0;
            for (String piece : hardSplit(text)) {
                int pieceStart = blockStart + localFrom;
                int pieceEnd = pieceStart + piece.length();
                localFrom += piece.length();
                boolean overLimit = !buffer.isEmpty() && buffer.length() + piece.length() + 1 > maxChars;
                if (overLimit) {
                    chunks.add(flush(buffer, bufferHeading, bufferPage, bufferCharStart, bufferCharEnd,
                            chunks.size() + 1));
                    buffer.setLength(0);
                }
                if (buffer.isEmpty()) {
                    bufferCharStart = pieceStart;
                    bufferHeading = blankToNull(block.headingPath());
                    bufferPage = block.pageNo();
                }
                if (!buffer.isEmpty()) {
                    buffer.append('\n');
                }
                buffer.append(piece);
                bufferCharEnd = pieceEnd;
            }
            blockStart += text.length() + 1;
        }
        if (!buffer.isEmpty()) {
            chunks.add(flush(buffer, bufferHeading, bufferPage, bufferCharStart, bufferCharEnd,
                    chunks.size() + 1));
        }
        return chunks;
    }

    private Chunk flush(StringBuilder buffer, String headingPath, int pageNo,
                        int charStart, int charEnd, int chunkNo) {
        String content = buffer.toString();
        int chars = content.replaceAll("\\s+", "").length();
        return new Chunk(chunkNo, truncate(headingPath), pageNo == 0 ? null : pageNo,
                charStart, charEnd, content, (int) Math.ceil(chars / 2.0), sha256Hex(content));
    }

    private List<String> hardSplit(String text) {
        List<String> pieces = new ArrayList<>();
        if (text.isEmpty()) {
            return pieces;
        }
        for (int from = 0; from < text.length(); ) {
            int to = Math.min(text.length(), from + maxChars);
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
