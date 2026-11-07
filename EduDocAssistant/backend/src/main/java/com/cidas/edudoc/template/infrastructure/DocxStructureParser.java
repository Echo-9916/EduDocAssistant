package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.ElementDraft;
import com.cidas.edudoc.template.domain.TemplateParseException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word 文档版本结构树解析（D-03，详细设计 §3.3.2 structureParser）：
 * 标题层级（章号 x.y 推导）、图/表元素（题注「图 2-1」式识别与章内编号）、代码块（样式识别，连续段并合）、
 * 公式（oMath 存在性）。位置口径：docx 无绝对页码，location_json 记段落索引（任务文档 R6）。
 * ponytail: EQUATION 用 XML 串包含判定，超大文档如需优化改 XmlCursor 局部扫描。
 */
@Component
public class DocxStructureParser {

    private static final Pattern CAPTION = Pattern.compile("^([图表])\\s*(\\d+)\\s*[-－.]\\s*(\\d+)");

    /** 解析中间态：可变节点，最后统一编号导出。 */
    private static final class Node {
        String type;
        String chapterNo;
        int bodyIndex;
        String label;
        String caption;
    }

    public List<ElementDraft> parse(byte[] docxBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            return doParse(doc);
        } catch (TemplateParseException e) {
            throw e;
        } catch (Exception e) {
            throw new TemplateParseException("结构树解析失败：" + e.getMessage(), e);
        }
    }

    private List<ElementDraft> doParse(XWPFDocument doc) {
        XWPFStyles styles = doc.getStyles();
        List<Node> nodes = new ArrayList<>();
        int[] counters = new int[4];
        int chapter = 0;
        int pendingFigure = -1;
        int lastCodeIndex = -2;

        int bodyIndex = 0;
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFTable) {
                Node table = node("TABLE", chapterOf(chapter), bodyIndex);
                // 表题在上：紧邻的前一个「表」题注占位节点并入表格元素
        // ponytail: 占位题注与表格相邻即并合；跨段引用场景留待真实语料反馈
                if (!nodes.isEmpty()) {
                    Node last = nodes.get(nodes.size() - 1);
                    if ("TABLE".equals(last.type) && last.caption != null && last.label != null) {
                        nodes.remove(last);
                        table.label = last.label;
                        table.caption = last.caption;
                    }
                }
                nodes.add(table);
                lastCodeIndex = -2;
            } else if (element instanceof XWPFParagraph paragraph) {
                String styleName = styleName(styles, paragraph);
                String text = paragraph.getText() == null ? "" : paragraph.getText().strip();
                int headingLevel = headingLevel(styleName);
                if (headingLevel > 0) {
                    counters[headingLevel]++;
                    for (int deeper = headingLevel + 1; deeper <= 3; deeper++) {
                        counters[deeper] = 0;
                    }
                    chapter = counters[1];
                    Node heading = node("HEADING", chapterPath(counters, headingLevel), bodyIndex);
                    heading.caption = text;
                    nodes.add(heading);
                } else if (isCaptionParagraph(styleName, text)) {
                    // 1106 审查 P1-2：先判 m.find()——样式题注但文本非「图/表 x-y」式时按首字归类、不得取分组崩溃
                    Matcher m = CAPTION.matcher(text);
                    boolean matched = m.find();
                    boolean figureToken = matched && "图".equals(m.group(1));
                    if (matched && figureToken && pendingFigure >= 0
                            && nodes.get(pendingFigure).label == null) {
                        Node figure = nodes.get(pendingFigure);
                        figure.label = m.group(0).strip();
                        figure.caption = text;
                        pendingFigure = -1;
                    } else if (matched || text.startsWith("图") || text.startsWith("表")) {
                        Node caption = new Node();
                        caption.type = matched ? (figureToken ? "FIGURE" : "TABLE")
                                : (text.startsWith("表") ? "TABLE" : "FIGURE");
                        caption.chapterNo = chapterOf(chapter);
                        caption.bodyIndex = bodyIndex;
                        caption.label = matched ? m.group(0).strip() : null;
                        caption.caption = text;
                        nodes.add(caption);
                        if (caption.type.equals("FIGURE")) {
                            pendingFigure = nodes.size() - 1;
                        }
                    }
                    // 样式题注但完全非图表形式的文本：当作普通段落，不产出元素
                } else if (hasImage(paragraph)) {
                    Node figure = node("FIGURE", chapterOf(chapter), bodyIndex);
                    nodes.add(figure);
                    pendingFigure = nodes.size() - 1;
                } else if (isCode(styleName)) {
                    if (bodyIndex != lastCodeIndex + 1) {
                        nodes.add(node("CODE", chapterOf(chapter), bodyIndex));
                    }
                    lastCodeIndex = bodyIndex;
                } else if (isEquation(paragraph)) {
                    nodes.add(node("EQUATION", chapterOf(chapter), bodyIndex));
                } else {
                    lastCodeIndex = -2;
                }
            }
            bodyIndex++;
        }
        return numbered(nodes);
    }

    private static String chapterOf(int chapter) {
        return String.valueOf(Math.max(chapter, 1));
    }

    private List<ElementDraft> numbered(List<Node> nodes) {
        Map<String, Integer> seq = new HashMap<>();
        List<ElementDraft> out = new ArrayList<>();
        for (Node n : nodes) {
            String key = switch (n.type) {
                case "HEADING" -> "H-" + n.bodyIndex;
                case "TABLE" -> "T-" + n.bodyIndex;
                case "FIGURE" -> "F-" + n.bodyIndex;
                case "CODE" -> "C-" + n.bodyIndex;
                default -> "E-" + n.bodyIndex;
            };
            Integer sequenceNo = null;
            String label = n.label;
            if (!"HEADING".equals(n.type) && !"REFERENCE".equals(n.type)) {
                sequenceNo = seq.merge(n.chapterNo + "|" + n.type, 1, Integer::sum);
                if (label == null) {
                    label = prefixOf(n.type) + " " + n.chapterNo + "-" + sequenceNo;
                }
            }
            out.add(new ElementDraft(key, n.type, n.chapterNo, sequenceNo, label, n.caption,
                    // 第五轮 P3-1：字符区间口径=段落文本内 [charStart,charEnd)（等价区间字段，D-11 按段渲染定位）
                    "{\"paragraphIndex\":" + n.bodyIndex + ",\"charStart\":0,\"charEnd\":"
                            + (n.caption == null ? 0 : n.caption.length()) + "}"));
        }
        return out;
    }

    private static String prefixOf(String type) {
        return switch (type) {
            case "TABLE" -> "表";
            case "FIGURE" -> "图";
            case "CODE" -> "代码";
            default -> "公式";
        };
    }

    private static Node node(String type, String chapterNo, int bodyIndex) {
        Node n = new Node();
        n.type = type;
        n.chapterNo = chapterNo;
        n.bodyIndex = bodyIndex;
        return n;
    }

    private static boolean isCaptionParagraph(String styleName, String text) {
        boolean styleCaption = styleName != null
                && (styleName.contains("caption") || styleName.contains("题注"));
        return styleCaption || CAPTION.matcher(text).find();
    }

    private static String styleName(XWPFStyles styles, XWPFParagraph paragraph) {
        if (styles == null || paragraph.getStyle() == null) {
            return null;
        }
        XWPFStyle style = styles.getStyle(paragraph.getStyle());
        return style == null || style.getName() == null
                ? null : style.getName().trim().toLowerCase(Locale.ROOT);
    }

    /** 终审 C3：仅 1..3 级计入（counters 词表边界），更深层级按正文处理不越界。 */
    private static int headingLevel(String styleName) {
        if (styleName == null) {
            return 0;
        }
        String normalized = styleName.replace("标题", "heading");
        Matcher m = Pattern.compile("^heading\\s+([1-3])$").matcher(normalized);
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String chapterPath(int[] counters, int level) {
        StringBuilder path = new StringBuilder();
        for (int i = 1; i <= level; i++) {
            if (i > 1) {
                path.append('.');
            }
            path.append(counters[i]);
        }
        return path.toString();
    }

    private static boolean isCode(String styleName) {
        return styleName != null && (styleName.contains("code") || styleName.contains("代码"));
    }

    private static boolean hasImage(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (!run.getEmbeddedPictures().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEquation(XWPFParagraph paragraph) {
        return paragraph.getCTP().toString().contains("oMath");
    }
}
