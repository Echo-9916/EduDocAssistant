package com.cidas.edudoc.document.infrastructure;

import com.cidas.edudoc.document.domain.TextBlock;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-06 Tika 提取：docx 标题栈与 pdf 页码（程序生成样本，不放二进制 fixture）。 */
class TikaTextExtractorTest {

    private final TikaTextExtractor extractor = new TikaTextExtractor();

    @TempDir
    Path dir;

    @Test
    void docxKeepsHeadingPathPerParagraph() throws Exception {
        Path file = dir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument(); var out = Files.newOutputStream(file)) {
            defineHeadingStyles(doc);
            heading(doc, "Heading1", "第一章 概述");
            paragraph(doc, "第一段正文内容。");
            heading(doc, "Heading2", "1.1 背景");
            paragraph(doc, "第二段正文内容。");
            doc.write(out);
        }

        List<TextBlock> blocks = extractor.extract(file, "sample.docx");

        TextBlock first = blocks.stream()
                .filter(b -> b.text().contains("第一段正文内容")).findFirst().orElseThrow();
        TextBlock second = blocks.stream()
                .filter(b -> b.text().contains("第二段正文内容")).findFirst().orElseThrow();
        assertTrue(first.headingPath().contains("第一章 概述"),
                "headingPath 未捕获标题，blocks=" + blocks);
        assertTrue(second.headingPath().contains("1.1 背景"), "blocks=" + blocks);
        assertTrue(!first.headingPath().contains("1.1 背景"), "兄弟标题不应污染浅层级");
    }

    @Test
    void docxTagsHeadingsListsAndTableCellsWithoutChangingText() throws Exception {
        Path file = dir.resolve("structure.docx");
        try (XWPFDocument doc = new XWPFDocument(); var out = Files.newOutputStream(file)) {
            doc.createParagraph().createRun().setText("一、项目概述");
            doc.createParagraph().createRun().setText("这是正文。");
            doc.createParagraph().createRun().setText("（1）列表项");
            var table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("表头A");
            table.getRow(0).getCell(1).setText("表头B");
            table.getRow(1).getCell(0).setText("值1");
            table.getRow(1).getCell(1).setText("值2");
            doc.write(out);
        }

        List<TextBlock> blocks = extractor.extract(file, "structure.docx");

        // 结构识别只加元数据：段落/列表/表格文字仍在同一条文本流里
        String full = String.join("\n", blocks.stream().map(b -> b.text().strip()).toList());
        assertTrue(full.contains("一、项目概述"));
        assertTrue(full.contains("（1）列表项"));
        assertTrue(full.contains("表头A"));

        TextBlock heading = find(blocks, "一、项目概述");
        assertEquals(TextBlock.HEADING, heading.kind());
        assertEquals(1, heading.level());

        TextBlock list = find(blocks, "（1）列表项");
        assertEquals(TextBlock.LIST_ITEM, list.kind());
        assertEquals(1, list.ordinal());

        TextBlock cell = find(blocks, "表头A");
        assertEquals(TextBlock.TABLE_CELL, cell.kind());
        assertEquals(1, cell.tableNo());
        assertEquals(1, cell.row());
        assertEquals(1, cell.col());
    }

    private static TextBlock find(List<TextBlock> blocks, String text) {
        return blocks.stream().filter(b -> b.text().contains(text)).findFirst().orElseThrow();
    }

    private static void defineHeadingStyles(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles();
        addNamedStyle(styles, "Heading1", "heading 1", 1);
        addNamedStyle(styles, "Heading2", "heading 2", 2);
    }

    private static void addNamedStyle(XWPFStyles styles, String id, String name, int level) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle style =
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory.newInstance();
        style.setStyleId(id);
        style.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType.PARAGRAPH);
        style.addNewName().setVal(name);
        style.addNewPPr().addNewOutlineLvl().setVal(java.math.BigInteger.valueOf(level - 1));
        styles.addStyle(new XWPFStyle(style));
    }

    @Test
    void pdfIncrementsPageNumber() throws Exception {
        Path file = dir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument(); OutputStream out = Files.newOutputStream(file)) {
            for (String title : List.of("FIRST PAGE TEXT", "SECOND PAGE TEXT")) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(title);
                    content.endText();
                }
            }
            doc.save(out);
        }

        List<TextBlock> blocks = extractor.extract(file, "sample.pdf");

        assertTrue(blocks.stream().anyMatch(b -> b.text().contains("FIRST PAGE TEXT")));
        assertTrue(blocks.stream().anyMatch(b -> b.text().contains("SECOND PAGE TEXT")));
        assertEquals(2, blocks.stream().mapToInt(TextBlock::pageNo).max().orElse(0),
                "第二页块页码应为 2，实际: " + blocks);
    }

    @Test
    void acceptsRtfContentNamedAsLegacyDoc(@TempDir Path tmp) throws Exception {
        // 1103 复审 N1：Word 另存 RTF 是 .doc 的历史合法内容，判据必须与扩展名放行层一致
        Path rtf = tmp.resolve("legacy.doc");
        Files.write(rtf, "{\\rtf1\\ansi\\deff0 {\\fonttbl{\\f0 Times New Roman;}}\\pard HELLO RTF BODY\\par}".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII));

        List<TextBlock> blocks = extractor.extract(rtf, "legacy.doc");

        assertTrue(blocks.stream().anyMatch(b -> b.text().contains("HELLO RTF BODY")),
                "RTF 应被接受并提取文本，blocks=" + blocks);
    }

    @Test
    void rejectsSpoofedExtensionByDetectedContentType(@TempDir Path tmp) throws Exception {
        // 1103 审查 S5：纯文本改名 .pdf —— 会话可过（自报值），提取必须按真实探测类型拒绝
        Path fake = tmp.resolve("fake.pdf");
        Files.write(fake, "just plain text, not a pdf at all".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.io.IOException e = assertThrows(java.io.IOException.class,
                () -> new TikaTextExtractor().extract(fake, "fake.pdf"));
        assertTrue(e.getMessage().contains("实际类型"), e.getMessage());
    }

    @Test
    void surrogateTextSurvivesExtractAndChunkRoundTrip() throws Exception {
        // C-01：含代理对文本的提取-分块贯通例——块文本=全文闭区间切片、相邻块连续、代理对不切断
        Path file = dir.resolve("surrogate.docx");
        try (XWPFDocument doc = new XWPFDocument(); var out = Files.newOutputStream(file)) {
            paragraph(doc, "开篇段落 😀 emoji 结尾");
            paragraph(doc, "尾段 𠮷 生僻字 tail");
            doc.write(out);
        }

        List<com.cidas.edudoc.document.domain.TextBlock> blocks = extractor.extract(file, "surrogate.docx");
        String full = String.join("\n", blocks.stream()
                .map(b -> b.text() == null ? "" : b.text().strip())
                .filter(t -> !t.isEmpty())
                .toList());
        List<com.cidas.edudoc.document.domain.Chunk> chunks =
                new com.cidas.edudoc.document.domain.ChunkingService(20).chunk(blocks).chunks();

        assertTrue(!chunks.isEmpty(), "贯通例必须产出分块");
        assertEquals(0, chunks.get(0).charStart());
        int prevEnd = -1;
        for (var c : chunks) {
            assertEquals(prevEnd + 1, c.charStart(), "块间连续: chunkNo=" + c.chunkNo());
            assertEquals(full.substring(c.charStart(), c.charEnd() + 1), c.text(),
                    "块文本=全文切片: chunkNo=" + c.chunkNo());
            prevEnd = c.charEnd();
        }
        assertEquals(full.length() - 1, prevEnd, "末块覆盖到文尾");
    }

    private static void heading(XWPFDocument doc, String style, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setStyle(style);
        paragraph.createRun().setText(text);
    }

    private static void paragraph(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }
}
