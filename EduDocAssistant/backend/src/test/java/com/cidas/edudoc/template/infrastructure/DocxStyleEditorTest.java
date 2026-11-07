package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.FixAction;
import com.cidas.edudoc.template.domain.StyleSnapshot;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** D-05 修复引擎真往返：改字体/字号/行距/页边距后重提取，实际值必须等于期望值（R1 修复面四类）。 */
class DocxStyleEditorTest {

    private final DocxStyleEditor editor = new DocxStyleEditor();
    private final DocxStyleExtractor extractor = new DocxStyleExtractor();

    private byte[] problemDoc() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFRun run = doc.createParagraph().createRun();
            run.setText("标题");
            run.setFontFamily("宋体");
            run.setFontSize(14);
            XWPFRun bodyRun = doc.createParagraph().createRun();
            bodyRun.setText("正文");
            bodyRun.setFontFamily("宋体");
            CTPageMar mar = doc.getDocument().getBody().addNewSectPr().addNewPgMar();
            mar.setTop(BigInteger.valueOf(1440));
            mar.setBottom(BigInteger.valueOf(1440));
            mar.setLeft(BigInteger.valueOf(1800));
            mar.setRight(BigInteger.valueOf(1440));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** 第三轮 P1-1：修复动作的 ascii/eastAsia 两槽分别写回，中文槽不得被西文字体污染——修复后回读验证。 */
    @Test
    void fontFixWritesSlotsSeparately() throws Exception {
        byte[] source;
        try (XWPFDocument doc = new XWPFDocument()) {
            var run = doc.createParagraph().createRun();
            run.setText("中文标题");
            var rPr = run.getCTR().addNewRPr();
            rPr.addNewRFonts().setAscii("Times New Roman");
            rPr.getRFontsArray(0).setEastAsia("宋体");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            source = out.toByteArray();
        }

        StyleSnapshot fixed = extractor.extract(editor.apply(source, List.of(
                new FixAction.Font(0, "Times New Roman", "黑体"))));

        assertEquals("Times New Roman", fixed.paragraphs().get(0).family());
        assertEquals("黑体", fixed.paragraphs().get(0).familyEastAsia());
    }

    /** 第四轮 P1-1：纸型问题标记可修复后，Page 动作必须真写 pgSz——Letter→A4 修复后回读。 */
    @Test
    void pageActionFixesPaperSizeAndMargins() throws Exception {
        byte[] letter;
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("正文");
            var sectPr = doc.getDocument().getBody().addNewSectPr();
            sectPr.addNewPgSz().setW(java.math.BigInteger.valueOf(12240));
            sectPr.getPgSz().setH(java.math.BigInteger.valueOf(15840));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            letter = out.toByteArray();
        }

        StyleSnapshot after = extractor.extract(editor.apply(letter, List.of(new FixAction.Page(
                java.util.Map.of("top", 25.4, "bottom", 25.4, "left", 31.75, "right", 25.4,
                        "width", 210.0, "height", 297.0)))));

        assertEquals(210.0, after.page().widthMm(), 0.6);
        assertEquals(297.0, after.page().heightMm(), 0.6);
        assertEquals(31.75, after.page().left(), 0.1);
    }

    /** 第四轮 P1-2：固定行距修复动作写回后仍为固定磅值（不被降级为倍数）。 */
    @Test
    void lineActionFixesExactSpacing() throws Exception {
        byte[] source;
        try (XWPFDocument doc = new XWPFDocument()) {
            var p = doc.createParagraph();
            p.createRun().setText("行距错误段");
            var spacing = p.getCTP().addNewPPr().addNewSpacing();
            spacing.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.AUTO);
            spacing.setLine(java.math.BigInteger.valueOf(240));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            source = out.toByteArray();
        }

        StyleSnapshot after = extractor.extract(editor.apply(source, List.of(
                new FixAction.Line(0, "EXACT", 20.0))));

        assertEquals("EXACT", after.paragraphs().get(0).line().kind());
        assertEquals(20.0, after.paragraphs().get(0).line().value(), 0.01);
    }

    @Test
    void appliesFourFixKindsAndRoundTrips() throws Exception {
        List<FixAction> actions = List.of(
                new FixAction.Font(0, "黑体", null),
                new FixAction.Size(0, 16.0),
                new FixAction.Line(0, "AUTO", 1.5),
                new FixAction.Page(
                        Map.of("top", 25.4, "bottom", 25.4, "left", 31.75, "right", 25.4)));

        StyleSnapshot after = extractor.extract(editor.apply(problemDoc(), actions));

        StyleSnapshot.ParagraphFact heading = after.paragraphs().get(0);
        assertEquals("黑体", heading.family());
        assertEquals(16.0, heading.pt(), 0.01);
        assertEquals(1.5, heading.line().value(), 0.01);
        assertEquals(31.75, after.page().left(), 0.1);
        // 未选中的正文段落字体不受影响
        assertEquals("宋体", after.paragraphs().get(1).family());
    }
}
