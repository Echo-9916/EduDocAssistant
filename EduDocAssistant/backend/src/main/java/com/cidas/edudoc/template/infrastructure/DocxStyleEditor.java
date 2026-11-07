package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.FixAction;
import com.cidas.edudoc.template.domain.FixAction.Font;
import com.cidas.edudoc.template.domain.FixAction.Line;
import com.cidas.edudoc.template.domain.FixAction.Page;
import com.cidas.edudoc.template.domain.FixAction.Size;
import com.cidas.edudoc.template.domain.TemplateParseException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * POI 样式修复引擎（D-05/R1 修复面）：只改 run 字体双槽、字号、行距（倍数或固定磅值）、
 * sectPr 页边距与纸型（第四轮 P1-1：width/height 贯通 pgSz），其余 XML 原样保留；
 * 超出修复面的规则 auto_fixable 必须为 N。
 */
@Component
public class DocxStyleEditor {

    public byte[] apply(byte[] docxBytes, List<FixAction> actions) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            for (FixAction action : actions) {
                switch (action) {
                    case Font font -> {
                        XWPFParagraph paragraph = paragraphAt(doc, font.paragraphIndex());
                        if (paragraph != null) {
                            applyFont(paragraph, font.family(), font.eastAsia());
                        }
                    }
                    case Size size -> {
                        XWPFParagraph paragraph = paragraphAt(doc, size.paragraphIndex());
                        if (paragraph != null) {
                            paragraph.getRuns().forEach(run -> run.setFontSize(size.pt()));
                        }
                    }
                    case Line line -> {
                        XWPFParagraph paragraph = paragraphAt(doc, line.paragraphIndex());
                        if (paragraph != null) {
                            applyLine(paragraph, line.kind(), line.value());
                        }
                    }
                    case Page page -> applyPage(doc, page.marginsMm());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new TemplateParseException("样式修复写回失败：" + e.getMessage(), e);
        }
    }

    /** 第三轮 P1-1：ascii 只动西文槽、eastAsia 只动中文槽，缺侧不写。 */
    private static void applyFont(XWPFParagraph paragraph, String family, String eastAsia) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (family != null && !family.isBlank()) {
                run.setFontFamily(family);
            }
            if (eastAsia != null && !eastAsia.isBlank()) {
                var rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
                CTFonts fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
                fonts.setEastAsia(eastAsia);
            }
        }
    }

    /** 第四轮 P1-2：行距修复支持 AUTO 倍数与 EXACT/AT_LEAST 磅值两种表达。 */
    private static void applyLine(XWPFParagraph paragraph, String kind, double value) {
        if ("EXACT".equals(kind) || "AT_LEAST".equals(kind)) {
            var pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr()
                    : paragraph.getCTP().addNewPPr();
            CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
            spacing.setLineRule("EXACT".equals(kind) ? STLineSpacingRule.EXACT : STLineSpacingRule.AT_LEAST);
            spacing.setLine(BigInteger.valueOf(Math.round(value * 20)));
        } else {
            paragraph.setSpacingBetween(value);
        }
    }

    /** 页边距 + 纸型一次写全（P1-1：只写 pgMar 不写 pgSz 会制造「已修复但复审仍失败」假闭环）。 */
    private static void applyPage(XWPFDocument doc, Map<String, Double> values) {
        var body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        if (values.containsKey("top")) {
            mar.setTop(dxa(values.get("top")));
        }
        if (values.containsKey("bottom")) {
            mar.setBottom(dxa(values.get("bottom")));
        }
        if (values.containsKey("left")) {
            mar.setLeft(dxa(values.get("left")));
        }
        if (values.containsKey("right")) {
            mar.setRight(dxa(values.get("right")));
        }
        if (values.containsKey("width") || values.containsKey("height")) {
            CTPageSz sz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
            if (values.containsKey("width")) {
                sz.setW(dxa(values.get("width")));
            }
            if (values.containsKey("height")) {
                sz.setH(dxa(values.get("height")));
            }
        }
    }

    private static BigInteger dxa(double mm) {
        return BigInteger.valueOf(Math.round(mm * 1440 / 25.4));
    }

    /** 与提取器同口径：正文元素序号（含表格）定位段落。 */
    private static XWPFParagraph paragraphAt(XWPFDocument doc, int index) {
        int position = 0;
        for (IBodyElement element : doc.getBodyElements()) {
            if (position == index && element instanceof XWPFParagraph paragraph) {
                return paragraph;
            }
            position++;
        }
        return null;
    }
}
