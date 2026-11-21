package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.application.DocumentWordExportService;
import com.cidas.edudoc.document.application.StructureJson;
import com.cidas.edudoc.document.domain.StructureNode;
import com.cidas.edudoc.document.domain.TextBlock;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.FormatCheckService;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import com.cidas.edudoc.template.infrastructure.TemplateOutlineGenerator;
import com.cidas.edudoc.template.infrastructure.TemplateVersion;
import com.cidas.edudoc.template.infrastructure.TemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Word 导出：当前版本文本 + structure_json → DOCX。
 * 模板优先（document 绑定 → 课程已发布模板），以模板 DOCX 为基底保留 styles/numbering/页眉页脚；
 * 模板不可用时新建文档并按内置样式排版。只还原语义结构，不承诺页面版式与原件一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentWordExportServiceImpl implements DocumentWordExportService {

    private static final long MAX_TEMPLATE_BYTES = 20L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final CollaborationService collaborationService;
    private final FormatCheckService formatCheckService;
    private final TemplateVersionRepository templateVersionRepository;
    private final FormatRuleRepository formatRuleRepository;
    private final TemplateOutlineGenerator outlineGenerator;
    private final OssObjectRepository ossObjectRepository;
    private final OssStorageAdapter ossStorageAdapter;

    @Override
    public byte[] exportDocx(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        collaborationService.requireReadable(documentId);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!AuthContext.require().userId().equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        DocumentVersion version = versionRepository.findById(doc.getCurrentVersionId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "当前版本不存在"));
        return render(documentId, version);
    }

    /** 格式校验 worker 用：文本块编辑版本没有 DOCX 源文件，按该版本结构临时生成 DOCX 再校验。 */
    @Override
    public byte[] exportVersionDocx(String documentId, String versionId) {
        if (!Ids.isHex16(documentId) || !Ids.isHex16(versionId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档或版本 ID 格式不合法");
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        DocumentVersion version = versionRepository.findById(versionId)
                .filter(v -> v.getDocumentId().equalsIgnoreCase(doc.getId()))
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "版本不存在"));
        return render(documentId, version);
    }

    private byte[] render(String documentId, DocumentVersion version) {
        List<DocumentChunk> chunks = chunkRepository.selectAllByVersion(version.getId());
        String fullText = chunks.stream().map(c -> c.getContentText() == null ? "" : c.getContentText())
                .collect(Collectors.joining());
        List<ExportBlock> blocks = toBlocks(fullText, StructureJson.read(version.getStructureJson()));
        String templateVersionId = formatCheckService.resolveTemplateVersionId(documentId);
        List<FormatRule> rules = templateVersionId == null ? List.of()
                : formatRuleRepository.findByVersion(templateVersionId);

        try (XWPFDocument out = openTemplate(templateVersionId)) {
            writeBlocks(out, blocks, rules);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            out.write(bytes);
            return bytes.toByteArray();
        } catch (CidasBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Word 导出失败 documentId={}", documentId, e);
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "Word 导出失败");
        }
    }

    /** 以模板 DOCX 为基底（保留样式/编号/页眉页脚），清空正文后重新写入。 */
    private XWPFDocument openTemplate(String templateVersionId) {
        if (templateVersionId != null) {
            TemplateVersion tv = templateVersionRepository.findById(templateVersionId).orElse(null);
            if (tv != null && tv.getSourceObjectId() != null) {
                OssObject object = ossObjectRepository.findById(tv.getSourceObjectId()).orElse(null);
                if (object != null) {
                    try {
                        byte[] bytes = ossStorageAdapter.readBytes(object.getObjectKey(), MAX_TEMPLATE_BYTES);
                        if (bytes.length > 1 && bytes[0] == 'P' && bytes[1] == 'K') {
                            XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                            for (int i = doc.getBodyElements().size() - 1; i >= 0; i--) {
                                doc.removeBodyElement(i);
                            }
                            return doc;
                        }
                    } catch (Exception e) {
                        log.warn("模板读取失败，回落内置样式 templateVersionId={}", templateVersionId, e);
                    }
                }
            }
        }
        return new XWPFDocument();
    }

    private static List<ExportBlock> toBlocks(String full, List<StructureNode> nodes) {
        if (nodes.isEmpty()) {
            List<ExportBlock> fallback = new ArrayList<>();
            for (String line : full.split("\\n")) {
                fallback.add(new ExportBlock(TextBlock.PARAGRAPH, null, line, null, null, null));
            }
            return fallback;
        }
        List<ExportBlock> blocks = new ArrayList<>();
        for (StructureNode node : nodes) {
            if (node.start() == null) {
                if (node.text() != null && !node.text().isBlank()) {
                    blocks.add(new ExportBlock(TextBlock.HEADING, node.level(), node.text(), null, null, null));
                }
                continue;
            }
            int start = Math.max(0, Math.min(node.start(), full.length()));
            int end = Math.max(start - 1, Math.min(node.end() == null ? start : node.end(), full.length() - 1));
            String text = end < start ? "" : full.substring(start, end + 1);
            blocks.add(new ExportBlock(node.kind(), node.level(), text, node.tableNo(), node.row(), node.col()));
        }
        return blocks;
    }

    private void writeBlocks(XWPFDocument doc, List<ExportBlock> blocks, List<FormatRule> rules) {
        for (int i = 0; i < blocks.size(); i++) {
            ExportBlock block = blocks.get(i);
            if (TextBlock.TABLE_CELL.equals(block.kind())) {
                List<ExportBlock> cells = new ArrayList<>();
                while (i < blocks.size() && TextBlock.TABLE_CELL.equals(blocks.get(i).kind())) {
                    cells.add(blocks.get(i++));
                }
                i--;
                writeTable(doc, cells, rules);
                continue;
            }
            if (TextBlock.HEADING.equals(block.kind())) {
                writeHeading(doc, block, rules);
            } else if (TextBlock.CODE.equals(block.kind())) {
                writeCode(doc, block.text());
            } else if (TextBlock.LIST_ITEM.equals(block.kind())) {
                writeParagraph(doc, "• " + block.text(), rules);
            } else {
                writeParagraph(doc, block.text(), rules);
            }
        }
    }

    private void writeHeading(XWPFDocument doc, ExportBlock block, List<FormatRule> rules) {
        int level = block.level() == null ? 1 : Math.min(3, Math.max(1, block.level()));
        XWPFParagraph p = doc.createParagraph();
        String styleId = resolveHeadingStyle(doc, level);
        if (styleId != null) {
            p.setStyle(styleId);
        }
        XWPFRun run = p.createRun();
        run.setText(block.text() == null ? "" : block.text());
        if (styleId == null) {
            run.setBold(true);
            run.setFontSize(level == 1 ? 18 : 15);
        }
        outlineGenerator.applyParagraphFormat(p, rules, "HEADING_" + level);
    }

    private static String resolveHeadingStyle(XWPFDocument doc, int level) {
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return null;
        }
        for (String candidate : List.of("Heading" + level, String.valueOf(level), "heading " + level)) {
            if (styles.getStyle(candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    private void writeParagraph(XWPFDocument doc, String text, List<FormatRule> rules) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text == null ? "" : text);
        outlineGenerator.applyParagraphFormat(p, rules, "BODY");
    }

    private static void writeCode(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.getCTP().addNewPPr().addNewShd().setFill("F2F2F2");
        XWPFRun run = p.createRun();
        run.setFontFamily("Consolas");
        run.setFontSize(9);
        String[] lines = (text == null ? "" : text).split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i]);
        }
    }

    /** 连续 TABLE_CELL 按 row 分组；缺 row/col 时按出现顺序兜底。 */
    private void writeTable(XWPFDocument doc, List<ExportBlock> cells, List<FormatRule> rules) {
        Map<Integer, List<ExportBlock>> rows = new TreeMap<>();
        int fallbackRow = 1;
        for (ExportBlock cell : cells) {
            int row = cell.row() == null ? fallbackRow++ : cell.row();
            rows.computeIfAbsent(row, k -> new ArrayList<>()).add(cell);
        }
        int cols = rows.values().stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = doc.createTable(rows.size(), cols);
        int r = 0;
        for (List<ExportBlock> row : rows.values()) {
            row.sort((a, b) -> Integer.compare(a.col() == null ? 0 : a.col(), b.col() == null ? 0 : b.col()));
            for (int c = 0; c < cols; c++) {
                XWPFTableCell cell = table.getRow(r).getCell(c);
                cell.setText(c < row.size() ? row.get(c).text() : "");
                cell.getParagraphs().forEach(p -> outlineGenerator.applyParagraphFormat(p, rules, "BODY"));
            }
            r++;
        }
    }

    private record ExportBlock(String kind, Integer level, String text,
                               Integer tableNo, Integer row, Integer col) {
    }
}