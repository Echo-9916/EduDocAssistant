package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.ai.application.AiGateway.PolishOutcome;
import com.cidas.edudoc.ai.application.impl.PolishServiceImpl;
import com.cidas.edudoc.ai.application.impl.PolishTxWriter;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.ai.infrastructure.PolishResult;
import com.cidas.edudoc.ai.infrastructure.PolishResultRepository;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.DocumentParseTxService;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.document.infrastructure.ParseProperties;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C-07 润色：段落校验（40001）、采纳成版五写、REVERT 仅最近一次、并发采纳 40901、REJECT 不动版本。
 * REVERT 实现口径 = 弹栈回拨（表约束无 REVERT 决策值，审计记史，见任务台账裁决）。
 */
class PolishServiceTest {

    private static final String DOC_ID = "AA0000000000000000000000000000A1";
    private static final String VERSION = "AA0000000000000000000000000000B1";
    private static final String POLISH_ID = "AA0000000000000000000000000000D1";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final ChunkEmbeddingRepository embeddingRepository = mock(ChunkEmbeddingRepository.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final PolishResultRepository polishRepository = mock(PolishResultRepository.class);
    private final PolishTxWriter txWriter = mock(PolishTxWriter.class);
    private final OssStorageAdapter ossAdapter = mock(OssStorageAdapter.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PolishService service = new PolishServiceImpl(documentRepository, versionRepository,
            embeddingRepository, aiGateway, polishRepository, txWriter, ossAdapter,
            new OssProperties("e", "r", "b", "ak", "sk", "arn", "sts", "edudoc/uploads", 1, 2), auditService);

    @BeforeEach
    void login() {
        AuthContext.set(new UserPrincipal("U1", "stu", null, Set.of(), Set.of("AI_POLISH"), Set.of()));
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private void givenReadyDoc() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "标题", "PDF", "READY", VERSION)));
    }

    private PolishResult currentRow;

    private void givenRow(String decision, String source, String polished) {
        currentRow = new PolishResult(POLISH_ID, DOC_ID, VERSION, "U1", "T1", source, polished, null, decision);
        when(polishRepository.findById(POLISH_ID)).thenReturn(Optional.of(currentRow));
    }

    // ---------- polish 请求 ----------

    @Test
    void rejectsOverLimitWith40001() {
        givenReadyDoc();
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.polish(DOC_ID, List.of("字".repeat(5001))));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void rejectsEmptyParagraphs() {
        givenReadyDoc();
        assertThrows(CidasBusinessException.class, () -> service.polish(DOC_ID, List.of()));
        assertThrows(CidasBusinessException.class, () -> service.polish(DOC_ID, List.of("  ")));
    }

    @Test
    void oneRowPerParagraphPendingWithSyncTask() {
        givenReadyDoc();
        when(aiGateway.polish(anyList())).thenReturn(new PolishOutcome("m", "p", null,
                List.of("润一", "润二")));

        List<PolishService.PolishRowView> views = service.polish(DOC_ID, List.of("原文一", "原文二"));

        assertEquals(2, views.size());
        assertEquals("PENDING", views.get(0).decision());
        ArgumentCaptor<List<PolishResult>> rows = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<AiTask> task = ArgumentCaptor.forClass(AiTask.class);
        // 审计已迁入写事务（1105 P1-2）：服务层只委托 persistRows 并带上模型元信息
        verify(txWriter).persistRows(task.capture(), rows.capture(), eq("m"), eq("p"));
        assertEquals("POLISH", task.getValue().getTaskType());
        assertEquals("SUCCEEDED", task.getValue().getStatus());
        assertEquals("原文一", rows.getValue().get(0).getSourceText());
        assertEquals("润二", rows.getValue().get(1).getPolishedText());
    }

    @Test
    void polishRejectsNotReadyDocWith40901() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "标题", "PDF", "PARSING", VERSION)));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.polish(DOC_ID, List.of("文")));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    // ---------- 采纳成版 ----------

    private void givenVersionText(String... chunkTexts) {
        when(versionRepository.findById(VERSION)).thenReturn(Optional.of(
                new DocumentVersion(VERSION, DOC_ID, 1, "UPLOAD", "O1", "U1", "h", "A")));
        java.util.List<VersionChunk> chunks = new java.util.ArrayList<>();
        int pos = 0;
        int no = 1;
        for (String t : chunkTexts) {
            chunks.add(new VersionChunk("CH" + no++, no, 1, pos, pos + t.length() - 1, t, "READY"));
            pos += t.length();
        }
        when(embeddingRepository.selectVersionChunks(VERSION)).thenReturn(chunks);
    }

    @Test
    void acceptReplacesTextAndDelegatesFiveWriteTx() {
        givenRow("PENDING", "旧段落", "新段落");
        givenReadyDoc();
        givenVersionText("前置。", "旧段落", "后置。");
        when(ossAdapter.putText(anyString(), anyString())).thenReturn(
                new OssStorageAdapter.ObjectInfo(10, "etag", null, "SSE-OSS"));
        when(txWriter.applyVersion(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PolishService.AppliedView(POLISH_ID, DOC_ID, "V2", 2, "PARSING"));

        PolishService.AppliedView view = service.apply(POLISH_ID, "ACCEPT", null, null);

        assertEquals("V2", view.versionId());
        ArgumentCaptor<String> newText = ArgumentCaptor.forClass(String.class);
        verify(ossAdapter).putText(anyString(), newText.capture());
        assertEquals("前置。新段落后置。", newText.getValue());
        ArgumentCaptor<OssObject> object = ArgumentCaptor.forClass(OssObject.class);
        verify(txWriter).applyVersion(object.capture(), any(), eq("U1"), eq("前置。新段落后置。"),
                eq(POLISH_ID), eq("ACCEPT"), eq("新段落"));
        assertEquals("PARSED", object.getValue().getObjectRole());
        assertEquals("text/plain", object.getValue().getMimeType());
        // POLISH_APPLIED 审计在 PolishTxWriter 事务内（见 writer 用例），服务层不再单独写
    }

    @Test
    void missingParagraphInLatestVersionConflicts40901() {
        givenRow("PENDING", "旧段落", "新段落");
        givenReadyDoc();
        givenVersionText("别的文本");

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.apply(POLISH_ID, "ACCEPT", null, null));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        verify(txWriter, never()).applyVersion(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void duplicateParagraphWithoutAnchorIsRejected() {
        // 1105 审查 P2-4：同文重复段落且未给 charStart → 拒绝消歧负担，不再静默替换首处
        givenRow("PENDING", "重复段", "新内容");
        givenReadyDoc();
        givenVersionText("重复段前置。", "重复段前置。");

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.apply(POLISH_ID, "ACCEPT", null, null));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        verify(ossAdapter, never()).putText(anyString(), anyString());
    }

    @Test
    void charStartAnchorReplacesExactOccurrence() {
        givenRow("PENDING", "重复段", "新内容");
        givenReadyDoc();
        givenVersionText("重复段", "中间", "重复段");
        when(ossAdapter.putText(anyString(), anyString())).thenReturn(
                new OssStorageAdapter.ObjectInfo(10, "etag", null, "SSE-OSS"));
        when(txWriter.applyVersion(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new PolishService.AppliedView(POLISH_ID, DOC_ID, "V2", 2, "READY"));

        // 全文=「重复段中间重复段」，charStart=5 → 替换第二处
        service.apply(POLISH_ID, "ACCEPT", null, 5);

        ArgumentCaptor<String> newText = ArgumentCaptor.forClass(String.class);
        verify(ossAdapter).putText(anyString(), newText.capture());
        assertEquals("重复段中间新内容", newText.getValue());
    }

    @Test
    void staleAnchorIsRejected() {
        givenRow("PENDING", "重复段", "新内容");
        givenReadyDoc();
        givenVersionText("开头不同", "别的", "内容");

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.apply(POLISH_ID, "ACCEPT", null, 3));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void alreadyDecidedRowConflicts() {
        givenRow("ACCEPT", "旧段落", "新段落");
        givenReadyDoc();
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.apply(POLISH_ID, "ACCEPT", null, null));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void partialRequiresAcceptedTextAndForeignRow40401() {
        givenRow("PENDING", "旧段落", "新段落");
        givenReadyDoc();
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.apply(POLISH_ID, "PARTIAL", " ", null)).errorCode());

        when(polishRepository.findById(POLISH_ID)).thenReturn(Optional.of(
                new PolishResult(POLISH_ID, DOC_ID, VERSION, "OTHER", "T1", "旧", "新", null, "PENDING")));
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(CidasBusinessException.class,
                () -> service.apply(POLISH_ID, "ACCEPT", null, null)).errorCode());
    }

    @Test
    void rejectOnlyMarksDecisionNoVersionWrites() {
        givenRow("PENDING", "旧段落", "新段落");
        when(polishRepository.updateDecisionIfPending(POLISH_ID, "REJECT", null)).thenReturn(true);

        service.reject(POLISH_ID);

        verify(ossAdapter, never()).putText(anyString(), anyString());
        verify(txWriter, never()).applyVersion(any(), any(), any(), any(), any(), any(), any());
        // 审查发现 1：审计第四参必须是 documentId（fk_audit_log_2），不能传 polishId
        verify(auditService).record(eq("POLISH_REJECTED"), eq("POLISH_RESULT"), eq(POLISH_ID),
                eq(DOC_ID), eq("200"), any());
    }

    @Test
    void concurrentDecisionGuarded40901() {
        givenRow("PENDING", "旧", "新");
        when(polishRepository.updateDecisionIfPending(POLISH_ID, "REJECT", null)).thenReturn(false);
        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> service.reject(POLISH_ID));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    // ---------- 撤销 ----------

    @Test
    void revertOnlyLatestAppliedEditVersion() {
        givenRow("ACCEPT", "旧段落", "新段落");
        givenReadyDoc();
        when(polishRepository.findLatestApplied(DOC_ID)).thenReturn(Optional.of(currentRow));
        when(polishRepository.findAppliedVersionIds(POLISH_ID)).thenReturn(List.of(VERSION));
        when(versionRepository.findById(VERSION)).thenReturn(Optional.of(
                new DocumentVersion(VERSION, DOC_ID, 2, "EDIT", "O2", "U1", "h", "A")));
        when(versionRepository.findByDocumentAndVersionNo(DOC_ID, 1)).thenReturn(Optional.of(
                new DocumentVersion("V1", DOC_ID, 1, "UPLOAD", "O1", "U1", "h", "A")));

        service.revert(POLISH_ID);

        verify(txWriter).revertToPrevious(any(Document.class), eq("V1"), eq(POLISH_ID));
    }

    @Test
    void revertRejectsWhenCurrentNotCreatedByThisRowApply() {
        // 审查发现 3：撤销前校验当前版本确由该行最近一次采纳产生（经 POLISH_APPLIED 审计回溯）
        givenRow("ACCEPT", "旧", "新");
        givenReadyDoc();
        when(polishRepository.findLatestApplied(DOC_ID)).thenReturn(Optional.of(currentRow));
        when(polishRepository.findAppliedVersionIds(POLISH_ID)).thenReturn(List.of("EE0000000000000000000000000000ZZ"));
        when(versionRepository.findById(VERSION)).thenReturn(Optional.of(
                new DocumentVersion(VERSION, DOC_ID, 2, "EDIT", "O2", "U1", "h", "A")));
        when(versionRepository.findByDocumentAndVersionNo(DOC_ID, 1)).thenReturn(Optional.of(
                new DocumentVersion("V1", DOC_ID, 1, "UPLOAD", "O1", "U1", "h", "A")));

        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> service.revert(POLISH_ID));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        verify(txWriter, never()).revertToPrevious(any(), anyString(), anyString());
    }

    @Test
    void revertRejectsOrphanPredecessorFromSameRow() {
        // 撤销→重采纳→再撤销：前一版本是同一行撤销留下的孤儿 EDIT，弹栈会回拨到错误内容 → 40901
        givenRow("ACCEPT", "旧", "新");
        givenReadyDoc();
        when(polishRepository.findLatestApplied(DOC_ID)).thenReturn(Optional.of(currentRow));
        when(polishRepository.findAppliedVersionIds(POLISH_ID)).thenReturn(List.of("V1", VERSION));
        when(versionRepository.findById(VERSION)).thenReturn(Optional.of(
                new DocumentVersion(VERSION, DOC_ID, 3, "EDIT", "O2", "U1", "h", "A")));
        when(versionRepository.findByDocumentAndVersionNo(DOC_ID, 2)).thenReturn(Optional.of(
                new DocumentVersion("V1", DOC_ID, 2, "EDIT", "O1", "U1", "h", "A")));

        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> service.revert(POLISH_ID));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        verify(txWriter, never()).revertToPrevious(any(), anyString(), anyString());
    }

    @Test
    void multiRowChainRevertStillWorks() {
        // 行1 采纳 v2、行2 采纳 v3：撤销行2 → 弹回 v2（v2 非本行产物，允许）
        givenRow("ACCEPT", "旧", "新");
        givenReadyDoc();
        when(polishRepository.findLatestApplied(DOC_ID)).thenReturn(Optional.of(currentRow));
        when(polishRepository.findAppliedVersionIds(POLISH_ID)).thenReturn(List.of(VERSION));
        when(versionRepository.findById(VERSION)).thenReturn(Optional.of(
                new DocumentVersion(VERSION, DOC_ID, 3, "EDIT", "O2", "U1", "h", "A")));
        when(versionRepository.findByDocumentAndVersionNo(DOC_ID, 2)).thenReturn(Optional.of(
                new DocumentVersion("V2_OTHER", DOC_ID, 2, "EDIT", "O9", "U1", "h", "A")));

        service.revert(POLISH_ID);

        verify(txWriter).revertToPrevious(any(Document.class), eq("V2_OTHER"), eq(POLISH_ID));
    }

    @Test
    void revertBlockedWhenNotLatestOrCurrentNotEdit() {
        givenRow("ACCEPT", "旧", "新");
        givenReadyDoc();
        when(polishRepository.findLatestApplied(DOC_ID)).thenReturn(Optional.of(
                new PolishResult("OTHER_ROW", DOC_ID, VERSION, "U1", "T", "旧", "新", null, "ACCEPT")));
        assertEquals(ErrorCode.CONFLICT, assertThrows(CidasBusinessException.class,
                () -> service.revert(POLISH_ID)).errorCode());

        when(polishRepository.findLatestApplied(DOC_ID)).thenReturn(Optional.of(currentRow));
        when(versionRepository.findById(VERSION)).thenReturn(Optional.of(
                new DocumentVersion(VERSION, DOC_ID, 3, "UPLOAD", "O3", "U1", "h", "A")));
        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> service.revert(POLISH_ID));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
        verify(txWriter, never()).revertToPrevious(any(), anyString(), anyString());
    }

    @Test
    void revertPendingRowConflicts() {
        givenRow("PENDING", "旧", "新");
        assertEquals(ErrorCode.CONFLICT, assertThrows(CidasBusinessException.class,
                () -> service.revert(POLISH_ID)).errorCode());
    }

    // ---------- 五写事务本体（PolishTxWriter） ----------

    @Test
    void applyVersionWritesObjectVersionPointerStatusChunksInOneCall() {
        DocumentParseTxService parseTx = mock(DocumentParseTxService.class);
        OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
        AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
        PolishTxWriter writer = new PolishTxWriter(aiTaskRepository, polishRepository,
                ossObjectRepository, versionRepository, documentRepository, parseTx,
                new ParseProperties(true, 1, "s", "g", 1500), auditService);
        Document doc = new Document(DOC_ID, null, "U1", "标题", "PDF", "READY", VERSION);
        OssObject derived = new OssObject("O2", "b", "r", "k", null, "etag", "hash", 5,
                "text/plain", "SSE-OSS", "DERIVED");
        when(versionRepository.maxVersionNo(DOC_ID)).thenReturn(1);
        when(polishRepository.updateDecisionIfPending(eq(POLISH_ID), eq("ACCEPT"), any())).thenReturn(true);
        when(parseTx.writeSuccess(eq(DOC_ID), anyString(), anyList())).thenReturn(true);

        PolishService.AppliedView view = writer.applyVersion(derived, doc, "U1",
                "第一段\n第二段", POLISH_ID, "ACCEPT", "第二段");

        assertEquals(2, view.versionNo());
        verify(ossObjectRepository).insert(derived);
        ArgumentCaptor<DocumentVersion> version = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionRepository).insert(version.capture());
        assertEquals("EDIT", version.getValue().getVersionType());
        assertEquals("O2", version.getValue().getSourceObjectId());
        verify(documentRepository).updateCurrentVersion(eq(DOC_ID), anyString());
        verify(documentRepository).updateStatus(DOC_ID, "PARSING");
        // 简化解析：段落成块 + 同事务补发向量化（writeSuccess 内部完成）
        ArgumentCaptor<List<com.cidas.edudoc.document.domain.Chunk>> chunks = ArgumentCaptor.forClass(List.class);
        verify(parseTx).writeSuccess(eq(DOC_ID), anyString(), chunks.capture());
        assertEquals(1, chunks.getValue().size());
        assertNotNull(view.versionId());
        // 1105 P2-4：同事务 writeSuccess 成功后响应回真实状态 READY（不再硬编码 PARSING）
        assertEquals("READY", view.documentStatus());
        // 1105 P1-2：POLISH_APPLIED 审计在成版同一事务内
        verify(auditService).record(eq("POLISH_APPLIED"), eq("DOCUMENT"), eq(DOC_ID), eq(DOC_ID),
                eq("200"), any());
    }

    @Test
    void revertToPreviousAuditsInSameTxWriter() {
        PolishTxWriter writer = new PolishTxWriter(mock(AiTaskRepository.class), polishRepository,
                mock(OssObjectRepository.class), versionRepository, documentRepository,
                mock(DocumentParseTxService.class), new ParseProperties(true, 1, "s", "g", 1500), auditService);
        writer.revertToPrevious(new Document(DOC_ID, null, "U1", "标题", "PDF", "READY", VERSION),
                "V0", POLISH_ID);
        verify(documentRepository).updateCurrentVersion(DOC_ID, "V0");
        verify(polishRepository).updateDecision(POLISH_ID, "PENDING", null);
        verify(auditService).record(eq("POLISH_REVERTED"), eq("DOCUMENT"), eq(DOC_ID), eq(DOC_ID),
                eq("200"), any());
    }

    @Test
    void concurrentVersionNoCollisionMapsTo40901() {
        // 审查发现 2：两条润色行并发采纳撞 uk(document_id, version_no) → 必须映射 40901 而非 50001
        DocumentParseTxService parseTx = mock(DocumentParseTxService.class);
        PolishTxWriter writer = new PolishTxWriter(mock(AiTaskRepository.class), polishRepository,
                mock(OssObjectRepository.class), versionRepository, documentRepository, parseTx,
                new ParseProperties(true, 1, "s", "g", 1500), auditService);
        when(versionRepository.maxVersionNo(DOC_ID)).thenReturn(1);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("uk_document_version"))
                .when(versionRepository).insert(any());

        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> writer.applyVersion(
                new OssObject("O2", "b", "r", "k", null, "e", "h", 1, "text/plain", "N", "DERIVED"),
                new Document(DOC_ID, null, "U1", "标题", "PDF", "READY", VERSION),
                "U1", "文本", POLISH_ID, "ACCEPT", "文本"));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void applyVersionGuardedDecisionLossRollsBack() {
        DocumentParseTxService parseTx = mock(DocumentParseTxService.class);
        PolishTxWriter writer = new PolishTxWriter(mock(AiTaskRepository.class), polishRepository,
                mock(OssObjectRepository.class), versionRepository, documentRepository, parseTx,
                new ParseProperties(true, 1, "s", "g", 1500), auditService);
        Document doc = new Document(DOC_ID, null, "U1", "标题", "PDF", "READY", VERSION);
        when(versionRepository.maxVersionNo(DOC_ID)).thenReturn(1);
        when(polishRepository.updateDecisionIfPending(anyString(), anyString(), any())).thenReturn(false);

        assertThrows(CidasBusinessException.class, () -> writer.applyVersion(
                new OssObject("O2", "b", "r", "k", null, "e", "h", 1, "text/plain", "N", "DERIVED"),
                doc, "U1", "文本", POLISH_ID, "ACCEPT", "文本"));
    }
}
