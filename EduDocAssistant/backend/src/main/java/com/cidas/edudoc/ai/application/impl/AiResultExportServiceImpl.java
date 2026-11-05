package com.cidas.edudoc.ai.application.impl;

import com.cidas.edudoc.ai.application.AiResultExportService;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiResultExportServiceImpl implements AiResultExportService {

    private final AiResultRepository resultRepository;
    private final AiTaskRepository taskRepository;
    private final DocumentRepository documentRepository;

    @Override
    public ExportFile export(String resultId, String format) {
        if (!Ids.isHex16(resultId) || (format != null && !format.isBlank()
                && !List.of("txt", "docx").contains(format.toLowerCase()))) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "format 仅支持 txt|docx");
        }
        AiResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        AiTask task = taskRepository.findById(result.getTaskId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND));
        if (!task.getRequestedBy().equalsIgnoreCase(AuthContext.require().userId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "结果不存在或不可见"); // 不越权读取他人结果
        }
        String title = documentRepository.findById(task.getDocumentId())
                .map(doc -> doc.getTitle()).orElse("AI结果");
        List<AiCitation> citations = resultRepository.findCitations(resultId);
        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if ("txt".equalsIgnoreCase(format)) {
            String body = buildText(title, result, citations);
            return new ExportFile(safeTitle + ".txt", MediaType.TEXT_PLAIN_VALUE,
                    body.getBytes(StandardCharsets.UTF_8));
        }
        return new ExportFile(safeTitle + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                buildDocx(title, result, citations));
    }

    /** 1105 审查 P3-3：标题按结果类型给名，KEYPOINT/ANSWER 不再误标「摘要」。 */
    private static String typeLabel(String resultType) {
        return switch (resultType == null ? "" : resultType) {
            case "KEYPOINT" -> "要点";
            case "ANSWER" -> "回答";
            case "POLISH" -> "润色";
            default -> "摘要";
        };
    }

    private String buildText(String title, AiResult result, List<AiCitation> citations) {
        StringBuilder sb = new StringBuilder(title).append('\n').append('\n');
        sb.append(result.getContentText() == null ? "" : result.getContentText()).append('\n');
        for (AiCitation c : citations) {
            sb.append("\n[引用 ").append(c.getRankNo()).append("] ")
                    .append(c.getPageNo() == null ? "" : "第 " + c.getPageNo() + " 页 ")
                    .append(c.getQuoteText() == null ? "" : c.getQuoteText());
        }
        return sb.toString();
    }

    private byte[] buildDocx(String title, AiResult result, List<AiCitation> citations) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph heading = doc.createParagraph();
            heading.createRun().setBold(true);
            heading.createRun().setText(title + " · " + typeLabel(result.getResultType()));
            String content = result.getContentText() == null ? "" : result.getContentText();
            for (String paragraph : content.split("\n")) {
                if (!paragraph.isBlank()) {
                    doc.createParagraph().createRun().setText(paragraph);
                }
            }
            for (AiCitation c : citations) {
                XWPFParagraph footnote = doc.createParagraph();
                String location = c.getPageNo() == null ? "" : "（第 " + c.getPageNo() + " 页）";
                footnote.createRun().setText("[引用 " + c.getRankNo() + "]" + location + " "
                        + (c.getQuoteText() == null ? "" : c.getQuoteText()));
            }
            XWPFParagraph meta = doc.createParagraph();
            meta.createRun().setText("模型：" + result.getModelName()
                    + "　提示词版本：" + result.getPromptVersion());
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "docx 导出失败：" + e.getMessage());
        }
    }
}
