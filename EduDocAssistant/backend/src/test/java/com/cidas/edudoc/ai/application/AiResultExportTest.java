package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.application.impl.AiResultExportServiceImpl;
import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C-04 导出最小实现：docx 可读且含引用、txt 直通、非本人 40401、非法 format 40001。 */
class AiResultExportTest {

    private static final String RESULT_ID = "AA0000000000000000000000000000D1";
    private static final String TASK_ID = "AA0000000000000000000000000000C1";
    private static final String DOC_ID = "AA0000000000000000000000000000A1";

    private final AiResultRepository resultRepository = mock(AiResultRepository.class);
    private final AiTaskRepository taskRepository = mock(AiTaskRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final AiResultExportService service =
            new AiResultExportServiceImpl(resultRepository, taskRepository, documentRepository);

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private void login(String userId) {
        AuthContext.set(new UserPrincipal(userId, "u", null, Set.of(), Set.of(), Set.of()));
    }

    private void givenResult(String requestedBy) {
        login("U1");
        when(resultRepository.findById(RESULT_ID)).thenReturn(Optional.of(
                new AiResult(RESULT_ID, TASK_ID, "SUMMARY", "第一段摘要\n第二段摘要", "{}",
                        "Kimi-K2", "sum-v1", 10, 5)));
        when(resultRepository.findCitations(RESULT_ID)).thenReturn(List.of(
                new AiCitation("X1", RESULT_ID, "CH1", 3, 10, 40, "引用原文片段", 1)));
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(
                new AiTask(TASK_ID, DOC_ID, "V1", "SUMMARY", "SUCCEEDED", 5, null, "{}",
                        requestedBy, 100, null, null, 0)));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, requestedBy, "课程*报告", "PDF", "READY", "V1")));
    }

    @Test
    void docxIsReadableAndContainsTitleBodyCitation() throws Exception {
        givenResult("U1");

        AiResultExportService.ExportFile file = service.export(RESULT_ID, "docx");

        assertTrue(file.filename().endsWith(".docx"));
        assertTrue(file.filename().contains("课程_报告"), "文件名清洗非法字符：" + file.filename());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(file.content()))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                text.append(p.getText()).append('\n');
            }
            assertTrue(text.toString().contains("第一段摘要"));
            assertTrue(text.toString().contains("引用原文片段"), "应含引用脚注段");
            assertTrue(text.toString().contains("Kimi-K2"), "应含模型元信息");
        }
    }

    @Test
    void keypointResultDocxTitleSaysKeypointNotSummary() throws Exception {
        // 1105 审查 P3-3：KEYPOINT 结果标题按类型给名
        givenResult("U1");
        when(resultRepository.findById(RESULT_ID)).thenReturn(java.util.Optional.of(
                new com.cidas.edudoc.ai.infrastructure.AiResult(RESULT_ID, TASK_ID, "KEYPOINT",
                        "1. 要点一", "{}", "Kimi-K2", "kp-v1", 10, 5)));
        AiResultExportService.ExportFile file = service.export(RESULT_ID, "docx");
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new java.io.ByteArrayInputStream(file.content()))) {
            String first = doc.getParagraphs().get(0).getText();
            assertTrue(first.contains("· 要点") && !first.contains("摘要"), first);
        }
    }

    @Test
    void txtExportsPlainTextBody() {
        givenResult("U1");

        AiResultExportService.ExportFile file = service.export(RESULT_ID, "txt");

        String text = new String(file.content(), StandardCharsets.UTF_8);
        assertTrue(text.contains("第一段摘要"));
        assertTrue(text.contains("[引用 1]"));
    }

    @Test
    void otherUsersResultIs40401() {
        givenResult("OTHER");

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.export(RESULT_ID, "docx"));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }

    @Test
    void unknownFormatRejectedWith40001() {
        login("U1");
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.export(RESULT_ID, "pdf"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void malformedIdRejectedAs40401WithoutQuery() {
        login("U1");
        when(resultRepository.findById(anyString())).thenReturn(Optional.empty());
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.export("not-a-hex", "docx"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }
}
