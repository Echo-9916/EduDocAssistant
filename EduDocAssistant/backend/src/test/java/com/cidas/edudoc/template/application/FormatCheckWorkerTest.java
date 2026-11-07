package com.cidas.edudoc.template.application;

import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.impl.FormatCheckTxServiceImpl;
import com.cidas.edudoc.template.domain.FormatRuleMatcher;
import com.cidas.edudoc.template.domain.IssueDraft;
import com.cidas.edudoc.template.domain.StyleSnapshot;
import com.cidas.edudoc.template.domain.TemplateParseException;
import com.cidas.edudoc.template.infrastructure.DocxStyleExtractor;
import com.cidas.edudoc.template.infrastructure.FormatCheck;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-04 Worker：CAS 过期跳过、PASSED/FAILED 汇总、结构树联动解析、异常 ERROR 态。 */
class FormatCheckWorkerTest {

    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final DocxStyleExtractor extractor = mock(DocxStyleExtractor.class);
    private final FormatRuleRepository ruleRepository = mock(FormatRuleRepository.class);
    private final FormatRuleMatcher matcher = mock(FormatRuleMatcher.class);
    private final DocumentStructureService structureService = mock(DocumentStructureService.class);
    private final FormatCheckRepository checkRepository = mock(FormatCheckRepository.class);
    private final FormatCheckTxService txService = mock(FormatCheckTxService.class);

    private final FormatCheckWorker worker = new FormatCheckWorker(
            mock(org.springframework.data.redis.core.StringRedisTemplate.class),
            ossObjectRepository, ossStorageAdapter, extractor, ruleRepository, matcher, structureService,
            checkRepository, txService,
            new com.cidas.edudoc.template.infrastructure.FormatCheckProperties(false, 1,
                    "edudoc:stream:format-check", "cg-format-check"),
            new com.fasterxml.jackson.databind.ObjectMapper());

    private void happy(FormatRule rule) throws Exception {
        Path temp = Files.createTempFile("wc-test", ".docx");
        Files.writeString(temp, "fake");
        when(ossObjectRepository.findById("OBJ1")).thenReturn(Optional.of(
                new OssObject("OBJ1", "b", "r", "k.docx", null, "e", "h", 4,
                        FormatCheckWorker.DOCX_MIME, "NONE", "ORIGINAL")));
        doAnswer(invocation -> {
            Files.copy(temp, invocation.getArgument(1), StandardCopyOption.REPLACE_EXISTING);
            return null;
        }).when(ossStorageAdapter).downloadTo(anyString(), any());
        when(extractor.extract(any())).thenReturn(new StyleSnapshot(List.of(),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true)));
    }

    private static FormatRule rule() {
        return new FormatRule("R1", "TV1", "FONT", "HEADING_1", "BLOCKER", "Y", "{}", 10);
    }

    @Test
    void staleEventSkipsWhenCasFails() throws Exception {
        when(checkRepository.markRunning("CH1")).thenReturn(0);

        worker.process("CH1", "DOC1", "VER1", "TV1", "OBJ1");

        verify(extractor, never()).extract(any());
    }

    @Test
    void failedCheckWritesIssuesAndBlockingCount() throws Exception {
        when(checkRepository.markRunning("CH1")).thenReturn(1);
        happy(rule());
        when(matcher.match(anyList(), any())).thenReturn(List.of(
                new IssueDraft("R1", "FONT", "PARAGRAPH", "{\"paragraphIndex\":1}", "黑体", "宋体",
                        "BLOCKER", "Y", "改字体"),
                new IssueDraft("R1", "SIZE", "PARAGRAPH", "{\"paragraphIndex\":2}", "12.0pt", "14.0pt",
                        "WARNING", "Y", "改字号")));

        worker.process("CH1", "DOC1", "VER1", "TV1", "OBJ1");

        ArgumentCaptor<List<IssueDraft>> issues = ArgumentCaptor.forClass(List.class);
        verify(txService).writeResult(eq("CH1"), issues.capture(), eq(2), eq(1), eq("FAILED"),
                eq("DOC1"), eq("VER1"));
        verify(structureService).parseAndPersist(any(), eq("VER1"));
    }

    @Test
    void cleanCheckPasses() throws Exception {
        when(checkRepository.markRunning("CH1")).thenReturn(1);
        happy(rule());
        when(matcher.match(anyList(), any())).thenReturn(List.of());

        worker.process("CH1", "DOC1", "VER1", "TV1", "OBJ1");

        verify(txService).writeResult(eq("CH1"), anyList(), eq(0), eq(0), eq("PASSED"),
                eq("DOC1"), eq("VER1"));
    }

    @Test
    void brokenDocxWritesErrorState() throws Exception {
        when(checkRepository.markRunning("CH1")).thenReturn(1);
        when(ossObjectRepository.findById("OBJ1")).thenReturn(Optional.of(
                new OssObject("OBJ1", "b", "r", "k.docx", null, "e", "h", 4,
                        FormatCheckWorker.DOCX_MIME, "NONE", "ORIGINAL")));
        when(extractor.extract(any())).thenThrow(new TemplateParseException("坏文件"));

        worker.process("CH1", "DOC1", "VER1", "TV1", "OBJ1");

        verify(txService).writeError(eq("CH1"), anyString(), eq("DOC1"), eq("VER1"));
        verify(txService, never()).writeResult(anyString(), any(), anyInt(), anyInt(), anyString(),
                anyString(), anyString());
    }

    /** 1106 审查 P1-1 回归闸：无 pStyle 正文段解析后带实际值进入比对（提取器修复的 worker 侧见证）。 */
    @Test
    void snapshotFlowsIntoMatcherUnfiltered() throws Exception {
        when(checkRepository.markRunning("CH1")).thenReturn(1);
        happy(rule());
        StyleSnapshot snap = new StyleSnapshot(List.of(
                new StyleSnapshot.ParagraphFact(0, "BODY", "宋体", null, 12.0, new StyleSnapshot.LineSpacing("AUTO", 1.0), "正文")),
                new StyleSnapshot.PageFacts(25.4, 25.4, 31.75, 25.4, 210.0, 297.0, true, true));
        when(extractor.extract(any())).thenReturn(snap);
        when(matcher.match(anyList(), any())).thenAnswer(inv -> {
            org.junit.jupiter.api.Assertions.assertEquals("宋体",
                    ((StyleSnapshot) inv.getArgument(1)).paragraphs().get(0).family());
            return List.of();
        });

        worker.process("CH1", "DOC1", "VER1", "TV1", "OBJ1");

        verify(matcher).match(anyList(), any());
    }
}
