package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.StyleSnapshot;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 终审 Minor 整改（提取器侧）：10.5pt 半磅精确、EXACT/AT_LEAST 固定行距不按倍数误读、裸「图N …」正文不误判题注。 */
class DocxStyleExtractorTest {

    private final DocxStyleExtractor extractor = new DocxStyleExtractor();

    private static CTStyle baseStyle(String id, String name) {
        CTStyle ct = CTStyle.Factory.newInstance();
        ct.setStyleId(id);
        ct.setType(STStyleType.PARAGRAPH);
        ct.addNewName().setVal(name);
        return ct;
    }

    private byte[] bytesOf(java.util.function.Consumer<XWPFDocument> build) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            styles.addStyle(new XWPFStyle(baseStyle("Cap", "caption")));
            build.accept(doc);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void halfPointSizeIsExact() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            var run = doc.createParagraph().createRun();
            run.setFontSize(10.5);
        });
        assertEquals(10.5, extractor.extract(bytes).paragraphs().get(0).pt(), 0.001);
    }

    @Test
    void exactLineRuleIsNotReadAsMultiplier() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            var p = doc.createParagraph();
            p.createRun().setText("固定行距段");
            CTSpacing spacing = p.getCTP().addNewPPr().addNewSpacing();
            spacing.setLineRule(STLineSpacingRule.EXACT);
            spacing.setLine(BigInteger.valueOf(480));
        });
        var line = extractor.extract(bytes).paragraphs().get(0).line();
        assertEquals("EXACT", line.kind());
        assertEquals(24.0, line.value(), 0.01);
    }

    @Test
    void autoLineRuleReadAsMultiplier() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            var p = doc.createParagraph();
            p.createRun().setText("倍数行距段");
            CTSpacing spacing = p.getCTP().addNewPPr().addNewSpacing();
            spacing.setLineRule(STLineSpacingRule.AUTO);
            spacing.setLine(BigInteger.valueOf(360));
        });
        assertEquals(1.5, extractor.extract(bytes).paragraphs().get(0).line().value(), 0.001);
    }

    /** 1106 审查 P1-1：无 pStyle 段落必须回退 Normal/docDefaults——继承样式的错误不得静默通过。 */
    @Test
    void paragraphWithoutStyleFallsBackToNormal() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            CTStyle normal = baseStyle("Normal", "Normal");
            normal.addNewRPr().addNewRFonts().setAscii("宋体");
            normal.getRPr().addNewSz().setVal(java.math.BigInteger.valueOf(21));
            doc.getStyles().addStyle(new XWPFStyle(normal));
            doc.createParagraph().createRun().setText("无样式正文段");
        });
        StyleSnapshot.ParagraphFact fact = extractor.extract(bytes).paragraphs().get(0);
        assertEquals("宋体", fact.family());
        assertEquals(10.5, fact.pt(), 0.001);
    }

    /** 1106 审查 P1-1：basedOn 一层继承（标题样式常继承 Normal 字体）。 */
    @Test
    void basedOnChainResolves() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            CTStyle parent = baseStyle("H1", "heading 1");
            parent.addNewRPr().addNewRFonts().setAscii("黑体");
            doc.getStyles().addStyle(new XWPFStyle(parent));
            CTStyle child = baseStyle("H2", "heading 2");
            child.addNewBasedOn().setVal("H1");
            child.addNewRPr().addNewSz().setVal(java.math.BigInteger.valueOf(24));
            doc.getStyles().addStyle(new XWPFStyle(child));
            var p = doc.createParagraph();
            p.setStyle("H2");
            p.createRun().setText("二级标题");
        });
        StyleSnapshot.ParagraphFact fact = extractor.extract(bytes).paragraphs().get(0);
        assertEquals("黑体", fact.family());
        assertEquals(12.0, fact.pt(), 0.001);
    }

    /** 1106 审查 P1-1：无任何行距设置时 Word 实际渲染单倍——默认值 1.0 必须可比对，不得为 null 漏检。 */
    @Test
    void unsetLineSpacingDefaultsToSingle() throws Exception {
        byte[] bytes = bytesOf(doc -> doc.createParagraph().createRun().setText("普通段"));
        assertEquals(1.0, extractor.extract(bytes).paragraphs().get(0).line().value(), 0.001);
    }

    /** 1106 审查 P2-2：pgSz 解析为物理宽高（mm），A4/Letter 才可比对。 */
    @Test
    void parsesPageSize() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            var sectPr = doc.getDocument().getBody().isSetSectPr()
                    ? doc.getDocument().getBody().getSectPr()
                    : doc.getDocument().getBody().addNewSectPr();
            sectPr.addNewPgSz().setW(java.math.BigInteger.valueOf(11906));
            sectPr.getPgSz().setH(java.math.BigInteger.valueOf(16838));
        });
        StyleSnapshot.PageFacts page = extractor.extract(bytes).page();
        assertEquals(210.0, page.widthMm(), 0.6);
        assertEquals(297.0, page.heightMm(), 0.6);
    }

    /** 复审 P1-2：段落仅设段前/段后（未设 line）时不得截断样式行距继承。 */
    @Test
    void beforeAfterSpacingDoesNotBreakLineInheritance() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            CTStyle normal = baseStyle("Normal", "Normal");
            CTSpacing styleSpacing = normal.addNewPPr().addNewSpacing();
            styleSpacing.setLineRule(STLineSpacingRule.AUTO);
            styleSpacing.setLine(java.math.BigInteger.valueOf(360));
            doc.getStyles().addStyle(new XWPFStyle(normal));

            var p = doc.createParagraph();
            p.createRun().setText("仅段前间距的正文段");
            CTSpacing paraSpacing = p.getCTP().addNewPPr().addNewSpacing();
            paraSpacing.setBefore(java.math.BigInteger.valueOf(240)); // 无 line
        });
        assertEquals(1.5, extractor.extract(bytes).paragraphs().get(0).line().value(), 0.001);
    }

    /** 复审 P1-1：East Asia 中文槽独立提取（ascii 相同不得掩盖中文槽）。 */
    @Test
    void eastAsiaFontSlotExtractedIndependently() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            var run = doc.createParagraph().createRun();
            run.setText("中文标题");
            var rPr = run.getCTR().addNewRPr();
            rPr.addNewRFonts().setAscii("Times New Roman");
            rPr.getRFontsArray(0).setEastAsia("黑体");
        });
        StyleSnapshot.ParagraphFact fact = extractor.extract(bytes).paragraphs().get(0);
        assertEquals("Times New Roman", fact.family());
        assertEquals("黑体", fact.familyEastAsia());
    }

    /** 第五轮 P2-5：run 显式属性下沉 RunFact（含字符区间），段落层为继承值。 */
    @Test
    void explicitRunPropertiesCarriedPerRunWithSpans() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            var p = doc.createParagraph();
            var a = p.createRun();
            a.setText("AAAA");
            a.setFontFamily("Arial");
            var b = p.createRun();
            b.setText("BB");
            b.setFontSize(20.0);
        });

        StyleSnapshot.ParagraphFact fact = extractor.extract(bytes).paragraphs().get(0);
        assertEquals(2, fact.runs().size());
        assertEquals("Arial", fact.runs().get(0).family());
        assertEquals(0, fact.runs().get(0).charStart());
        assertEquals(4, fact.runs().get(0).charEnd());
        assertNull(fact.runs().get(1).family());
        assertEquals(20.0, fact.runs().get(1).pt(), 0.001);
        assertEquals(4, fact.runs().get(1).charStart());
        // 兼容读取：首显式值
        assertEquals("Arial", fact.family());
        assertEquals(20.0, fact.pt(), 0.001);
    }

    /** 图/表开头的正文句不误判题注；「图x-y 名称」式题注仍识别（与解析器 dash 口径一致）。 */
    @Test
    void captionDetectionRequiresChapterPattern() throws Exception {
        byte[] bytes = bytesOf(doc -> {
            doc.createParagraph().createRun().setText("图1 展示了系统总体架构与模块划分情况");
            doc.createParagraph().createRun().setText("图2-1 系统架构图");
            var styled = doc.createParagraph();
            styled.setStyle("Cap");
            styled.createRun().setText("图3 无章号的题注样式段");
        });

        var facts = extractor.extract(bytes).paragraphs();
        assertEquals("BODY", facts.get(0).selector());
        assertEquals("FIGURE_CAPTION", facts.get(1).selector());
        assertEquals("FIGURE_CAPTION", facts.get(2).selector());
    }
}
