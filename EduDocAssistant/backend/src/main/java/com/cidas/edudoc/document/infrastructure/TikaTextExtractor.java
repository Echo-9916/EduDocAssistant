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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tika 文本提取：段落（p）为块，h1–h6 维护章节栈，页边界来自 div.page（PDFBox）或换页符。
 * 老式 .doc 结构识别较弱（POI HWPF），headingPath 允许为空（风险 R7）。
 * 1103 审查 S5：解析后按 Tika 真实探测的 Content-Type 复验（客户端自报值不可信）；
 * 提取文本设上限，防嵌套容器/内容爆炸（20MB 容器 ≠ 20MB 文本）。
 *
 * <p>结构识别（P0 实测：Tika 对 DOCX 的标题/列表样式也常输出普通 p，且无 class）：
 * td 祖先 → TABLE_CELL（可靠）；文本形态 → HEADING/LIST_ITEM（启发式，只加元数据、不改文本流）。
 * ponytail: 标题/列表靠正则，误判率换结构可读性；真要做准需换 POI outlineLvl/numPr 解析。
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

    private static final int MAX_HEADING_CHARS = 80;
    private static final Pattern HEADING_CN_CHAPTER =
            Pattern.compile("^第[一二三四五六七八九十百零〇]+[章节部分篇]");
    private static final Pattern HEADING_CN_NUM =
            Pattern.compile("^[一二三四五六七八九十]+、");
    private static final Pattern HEADING_NUMERIC =
            Pattern.compile("^(\\d+(?:\\.\\d+){0,2})\\s+\\S");
    private static final Pattern HEADING_KEYWORD =
            Pattern.compile("^(摘要|目录|参考文献|致谢|附录)$");
    private static final Pattern LIST_BULLET =
            Pattern.compile("^[•·▪◦●\\-–—]\\s*\\S");
    private static final Pattern LIST_NUM =
            Pattern.compile("^(?:（(\\d+)）|\\((\\d+)\\)|(\\d+)[.、)])\\s*\\S");

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

        // 表格结构：Tika DOCX 输出 <table><tr><td><p>，用祖先状态给 p 打 TABLE_CELL 标
        // ponytail: 只记最内层表格，嵌套表格按外层坐标处理
        private int tableNo = 0;
        private int rowNo = 0;
        private int colNo = 0;
        private boolean inCell = false;

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
            if ("table".equals(name)) {
                tableNo++;
                rowNo = 0;
                colNo = 0;
                return;
            }
            if ("tr".equals(name)) {
                rowNo++;
                colNo = 0;
                return;
            }
            if ("td".equals(name) || "th".equals(name)) {
                colNo++;
                inCell = true;
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
            String name = qName.toLowerCase();
            if ("td".equals(name) || "th".equals(name)) {
                inCell = false;
            }
            if (mode == null || !name.equals(mode)) {
                return;
            }
            String text = current.toString().strip();
            if ("p".equals(mode)) {
                String[] pages = text.split("\f", -1);
                for (int i = 0; i < pages.length; i++) {
                    if (!pages[i].isBlank()) {
                        blocks.add(blockOf(pages[i].strip()));
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

        /** 只加结构元数据，文本与偏移与改造前完全一致。 */
        private TextBlock blockOf(String text) {
            if (inCell) {
                return new TextBlock(text, headingPath(), page, TextBlock.TABLE_CELL, null,
                        null, tableNo, rowNo, colNo);
            }
            Heading heading = headingOf(text);
            if (heading != null) {
                return new TextBlock(text, headingPath(), page, TextBlock.HEADING, heading.level,
                        null, null, null, null);
            }
            Integer ordinal = listOrdinalOf(text);
            if (ordinal != null) {
                return new TextBlock(text, headingPath(), page, TextBlock.LIST_ITEM, null,
                        ordinal, null, null, null);
            }
            return new TextBlock(text, headingPath(), page);
        }

        private static Heading headingOf(String text) {
            if (text.length() > MAX_HEADING_CHARS) {
                return null;
            }
            if (HEADING_KEYWORD.matcher(text).matches() || HEADING_CN_CHAPTER.matcher(text).find()
                    || HEADING_CN_NUM.matcher(text).find()) {
                return new Heading(1);
            }
            Matcher numeric = HEADING_NUMERIC.matcher(text);
            if (numeric.find()) {
                // 4 位数字开头更像年份（“2026 年…”），不当标题
                if (numeric.group(1).split("\\.")[0].length() > 2) {
                    return null;
                }
                int level = Math.min(3, numeric.group(1).split("\\.").length);
                return new Heading(level);
            }
            return null;
        }

        /** 返回列表序号（无序列表返回 1），非列表返回 null。 */
        private static Integer listOrdinalOf(String text) {
            if (LIST_BULLET.matcher(text).find()) {
                return 1;
            }
            Matcher m = LIST_NUM.matcher(text);
            if (!m.find()) {
                return null;
            }
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) {
                    try {
                        return Integer.parseInt(m.group(i));
                    } catch (NumberFormatException ignored) {
                        return 1;
                    }
                }
            }
            return 1;
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

        private record Heading(int level) {
        }
    }
}