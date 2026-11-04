package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.document.domain.TextBlock;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tika 文本提取：段落（p）为块，h1–h6 维护章节栈，页边界来自 div.page（PDFBox）或换页符。
 * 老式 .doc 结构识别较弱（POI HWPF），headingPath 允许为空（风险 R7）。
 * 1103 审查 S5：解析后按 Tika 真实探测的 Content-Type 复验（客户端自报值不可信）；
 * 提取文本设上限，防嵌套容器/内容爆炸（20MB 容器 ≠ 20MB 文本）。
 */
@Component
public class TikaTextExtractor {

    /**
     * 与扩展名放行层判据对齐（1103 复审 N1 决策：扩接受，不预拒）：
     * .doc/.docx 的历史合法内容就是 rtf / Word 另存 HTML / Word2003 XML / OLE2·OOXML 泛型容器；
     * 纯文本（text/plain）仍拒绝——伪装扩展名不是 Word 家族产物。
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/x-tika-msoffice",
            "application/x-tika-ooxml",
            "application/rtf",
            "text/html",
            "text/xml",
            "application/xml");
    private static final int MAX_TEXT_CHARS = 8_000_000; // ponytail: 上限按 400 万字 ≈200 万 token，超过即视为内容爆炸

    private final AutoDetectParser parser = new AutoDetectParser();

    public List<TextBlock> extract(Path file, String resourceName) throws IOException {
        BlockCollector collector = new BlockCollector();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resourceName);
        try (InputStream in = Files.newInputStream(file)) {
            parser.parse(in, collector, metadata);
        } catch (SAXException | TikaException e) {
            throw new IOException("文档解析失败：" + e.getMessage(), e);
        }
        String detected = metadata.get(Metadata.CONTENT_TYPE);
        if (detected == null
                || !ALLOWED_CONTENT_TYPES.contains(detected.split(";")[0].strip().toLowerCase(Locale.ROOT))) {
            throw new IOException("文件实际类型不在支持范围：" + detected);
        }
        return collector.blocks();
    }

    static class BlockCollector extends DefaultHandler {

        private final List<TextBlock> blocks = new ArrayList<>();
        private final String[] headingLevels = new String[6];
        private final StringBuilder current = new StringBuilder();
        private String mode = null;
        private int page = 0;

        List<TextBlock> blocks() {
            return blocks;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = qName.toLowerCase();
            if ("div".equals(name)) {
                String css = attributes.getValue("class");
                if (css != null && css.contains("page")) {
                    page++;
                }
                return;
            }
            if ("p".equals(name) || (name.length() == 2 && name.charAt(0) == 'h'
                    && name.charAt(1) >= '1' && name.charAt(1) <= '6')) {
                mode = name;
                current.setLength(0);
            }
        }

        private long totalChars;

        @Override
        public void characters(char[] ch, int start, int length) {
            // 1103 复审 N5：上限无条件累计（含元素外文本）；IllegalStateException 非受检，
            // 原样穿透 extract()，由 worker catch(Exception) 兜住置 FAILED（行为正确，上一版注释写错）
            totalChars += length;
            if (totalChars > MAX_TEXT_CHARS) {
                throw new IllegalStateException("提取文本超过上限 " + MAX_TEXT_CHARS + " 字符");
            }
            if (mode != null) {
                current.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (mode == null || !qName.equalsIgnoreCase(mode)) {
                return;
            }
            String text = current.toString().strip();
            if ("p".equals(mode)) {
                String[] pages = text.split("\f", -1);
                for (int i = 0; i < pages.length; i++) {
                    if (!pages[i].isBlank()) {
                        blocks.add(new TextBlock(pages[i].strip(), headingPath(), page));
                    }
                    if (i < pages.length - 1) {
                        page++;
                    }
                }
            } else {
                int level = mode.charAt(1) - '1';
                headingLevels[level] = text.isEmpty() ? null : text;
                for (int i = level + 1; i < headingLevels.length; i++) {
                    headingLevels[i] = null;
                }
            }
            mode = null;
            current.setLength(0);
        }

        private String headingPath() {
            StringBuilder path = new StringBuilder();
            for (String level : headingLevels) {
                if (level != null && !level.isBlank()) {
                    if (!path.isEmpty()) {
                        path.append(" / ");
                    }
                    path.append(level);
                }
            }
            return path.toString();
        }
    }
}
