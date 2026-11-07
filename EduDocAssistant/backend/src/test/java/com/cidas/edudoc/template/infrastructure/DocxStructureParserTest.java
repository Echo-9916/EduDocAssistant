package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.ElementDraft;
import com.cidas.edudoc.template.domain.TemplateParseException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-03 版本结构树解析：标题层级/章号推导、图表题注、代码块元素与稳定 element_key。 */
class DocxStructureParserTest {

    private final DocxStructureParser parser = new DocxStructureParser();

    private static XWPFStyle paragraphStyle(String id, String name) {
        CTStyle ct = CTStyle.Factory.newInstance();
        ct.setStyleId(id);
        ct.setType(STStyleType.PARAGRAPH);
        ct.addNewName().setVal(name);
        return new XWPFStyle(ct);
    }

    private byte[] structuredDoc() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFStyles styles = doc.createStyles();
            styles.addStyle(paragraphStyle("H1", "heading 1"));
            styles.addStyle(paragraphStyle("H2", "heading 2"));
            styles.addStyle(paragraphStyle("H4", "heading 4"));
            styles.addStyle(paragraphStyle("Cap", "caption"));
            styles.addStyle(paragraphStyle("Code", "代码"));

            heading(doc, "H1", "绪论");
            doc.createParagraph().createRun().setText("正文一段。");
            heading(doc, "H1", "系统设计");
            heading(doc, "H2", "总体架构");

            var tableCaption = doc.createParagraph();
            tableCaption.setStyle("Cap");
            tableCaption.createRun().setText("表2-1 接口清单");
            doc.createTable(2, 2);

            var figure = doc.createParagraph();
            var png = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", png);
            figure.createRun().addPicture(new ByteArrayInputStream(png.toByteArray()),
                    Workbook.PICTURE_TYPE_PNG, "arch.png", 40, 40);
            var figureCaption = doc.createParagraph();
            figureCaption.setStyle("Cap");
            figureCaption.createRun().setText("图 2-1 架构图");

            var code = doc.createParagraph();
            code.setStyle("Code");
            code.createRun().setText("int main() {}");

            // 终审 C2/C3：裸「参考文献」段落与四级标题——前者不得产出字典外元素，后者不得越界
            var bareRef = doc.createParagraph();
            bareRef.createRun().setText("参考文献");
            var deep = doc.createParagraph();
            deep.setStyle("H4");
            deep.createRun().setText("过深的标题");

            // 1106 审查 P1-2：Caption 样式但文本非「图/表 x-y」式——不得抛 No match found 崩整次校验
            var looseCaption = doc.createParagraph();
            looseCaption.setStyle("Cap");
            looseCaption.createRun().setText("图3 无章号的题注样式段");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void heading(XWPFDocument doc, String styleId, String text) {
        var p = doc.createParagraph();
        p.setStyle(styleId);
        p.createRun().setText(text);
    }

    private ElementDraft find(List<ElementDraft> drafts, String type) {
        return drafts.stream().filter(d -> d.elementType().equals(type)).findFirst()
                .orElseThrow(() -> new AssertionError("缺少元素 " + type + "，实际=" + drafts));
    }

    @Test
    void headingsCarryChapterNumbers() throws Exception {
        List<ElementDraft> drafts = parser.parse(structuredDoc());

        List<ElementDraft> headings = drafts.stream()
                .filter(d -> d.elementType().equals("HEADING")).toList();
        assertEquals(3, headings.size());
        assertEquals("1", headings.get(0).chapterNo());
        assertEquals("2", headings.get(1).chapterNo());
        assertEquals("2.1", headings.get(2).chapterNo());
        assertEquals("绪论", headings.get(0).captionText());
        assertTrue(headings.get(0).locationJson().contains("paragraphIndex"));
        // element_key 版本内唯一
        assertEquals(headings.size(), headings.stream().map(ElementDraft::elementKey).distinct().count());
    }

    @Test
    void figuresTablesCodeWithLabelsAndChapters() throws Exception {
        List<ElementDraft> drafts = parser.parse(structuredDoc());

        ElementDraft table = find(drafts, "TABLE");
        assertEquals("2", table.chapterNo());
        assertEquals(1, table.sequenceNo());
        assertEquals("表2-1", table.displayLabel());
        assertEquals("表2-1 接口清单", table.captionText());

        ElementDraft figure = find(drafts, "FIGURE");
        assertEquals("2", figure.chapterNo());
        assertEquals("图 2-1", figure.displayLabel());

        ElementDraft code = find(drafts, "CODE");
        assertEquals("2", code.chapterNo());
        assertEquals(1, code.sequenceNo());
    }

    /** 1106 审查 P1-2：样式题注非章序文本按前缀字归类，不崩解析；图元素两个且都有显示编号。 */
    @Test
    void looseStyledCaptionDoesNotCrash() throws Exception {
        List<ElementDraft> drafts = parser.parse(structuredDoc());
        List<ElementDraft> figures = drafts.stream()
                .filter(d -> d.elementType().equals("FIGURE")).toList();
        assertEquals(2, figures.size());
        for (ElementDraft figure : figures) {
            assertTrue(figure.displayLabel() != null && !figure.displayLabel().isBlank(),
                    "图元素应补全编号：" + figure);
        }
    }

    /** 终审 C2：ck_element_type 只允许五类；「参考文献」裸段落不得产出 REFERENCE 元素。 */
    @Test
    void elementsStayWithinDictionary() throws Exception {
        List<ElementDraft> drafts = parser.parse(structuredDoc());
        List<String> allowed = List.of("HEADING", "FIGURE", "TABLE", "CODE", "EQUATION");
        for (ElementDraft draft : drafts) {
            assertTrue(allowed.contains(draft.elementType()), "越字典元素: " + draft);
        }
    }

    /** 终审 C3：Heading 4+ 不得越界计数（按正文处理），解析不崩。 */
    @Test
    void deepHeadingDoesNotCrash() throws Exception {
        List<ElementDraft> drafts = parser.parse(structuredDoc());
        List<ElementDraft> headings = drafts.stream()
                .filter(d -> d.elementType().equals("HEADING")).toList();
        assertEquals(3, headings.size());
    }

    @Test
    void rejectsNonDocx() {
        assertThrows(TemplateParseException.class, () -> parser.parse("junk".getBytes()));
    }
}
