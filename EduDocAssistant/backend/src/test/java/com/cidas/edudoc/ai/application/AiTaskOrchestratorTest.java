package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.application.impl.AiTaskOrchestratorImpl;
import com.cidas.edudoc.ai.infrastructure.AiResultRepository;
import com.cidas.edudoc.ai.infrastructure.AiTask;
import com.cidas.edudoc.ai.infrastructure.AiTaskRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository;
import com.cidas.edudoc.ai.infrastructure.ChunkEmbeddingRepository.VersionChunk;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C-04 编排：UT-08 活跃去重、40901 未就绪、40301 权限、非本人任务 40401、取消状态机。 */
class AiTaskOrchestratorTest {

    private static final String DOC_ID = "AA0000000000000000000000000000A1";
    private static final String VERSION = "AA0000000000000000000000000000B1";
    private static final String TASK_ID = "AA0000000000000000000000000000C1";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final ChunkEmbeddingRepository embeddingRepository = mock(ChunkEmbeddingRepository.class);
    private final AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
    private final AiResultRepository aiResultRepository = mock(AiResultRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final AiTaskOrchestrator orchestrator = new AiTaskOrchestratorImpl(documentRepository,
            embeddingRepository, aiTaskRepository, aiResultRepository, outboxEventRepository, new ObjectMapper());

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private static void login(Set<String> permissions) {
        // 种子角色均含 DOCUMENT_READ（审查 P2-3：组合校验后各用例默认带上）
        Set<String> all = new java.util.HashSet<>(permissions);
        all.add("DOCUMENT_READ");
        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"), all, Set.of()));
    }

    private static Document readyDoc(String ownerId) {
        return new Document(DOC_ID, null, ownerId, "标题", "PDF", "READY", VERSION);
    }

    private void givenReadyChunks() {
        when(embeddingRepository.countNotReady(VERSION)).thenReturn(0);
        when(embeddingRepository.selectVersionChunks(VERSION))
                .thenReturn(List.of(new VersionChunk("CH1", 1, 1, 0, 9, "文本", "READY")));
    }

    @Test
    void createsTaskWithOutboxEventInOneFlow() {
        login(Set.of("AI_SUMMARY"));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(readyDoc("U1")));
        givenReadyChunks();
        when(aiTaskRepository.findActive(VERSION, "SUMMARY", "U1")).thenReturn(Optional.empty());
        when(aiTaskRepository.insertOrReuse(any())).thenAnswer(inv -> inv.getArgument(0));

        String taskId = orchestrator.createTask(DOC_ID, "SUMMARY");

        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskRepository).insertOrReuse(captor.capture());
        assertEquals("QUEUED", captor.getValue().getStatus());
        assertEquals("SUMMARY", captor.getValue().getTaskType());
        assertEquals("U1", captor.getValue().getRequestedBy());
        // P2-5：确定性 ACTIVE 锁键
        assertEquals("ACTIVE:" + VERSION + ":SUMMARY:U1", captor.getValue().getIdempotencyKey());
        verify(outboxEventRepository).publish(eq("AI_TASK"), anyString(), eq("AI_TASK_REQUESTED"), any());
    }

    @Test
    void concurrentCreateCollisionReusesWinnerWithoutRepublish() {
        // 两入口 findActive 均 miss，输家 insertOrReuse 撞唯一键返回赢家任务 → 不重复投递
        login(Set.of("AI_SUMMARY"));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(readyDoc("U1")));
        givenReadyChunks();
        when(aiTaskRepository.findActive(VERSION, "SUMMARY", "U1")).thenReturn(Optional.empty());
        AiTask winner = new AiTask("WINNER_ID", DOC_ID, VERSION, "SUMMARY", "QUEUED", 5, null,
                "{}", "U1", 0, "ACTIVE:" + VERSION + ":SUMMARY:U1", null, 0);
        when(aiTaskRepository.insertOrReuse(any())).thenReturn(winner);

        String taskId = orchestrator.createTask(DOC_ID, "SUMMARY");

        assertEquals("WINNER_ID", taskId);
        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
    }

    @Test
    void cancelUsesQueuedTransition() {
        // 锁键释放已并入 cancelQueued/cancelRunning 的终态 SQL（1106 复审 P2-2），服务层不再单独释放
        login(Set.of("AI_SUMMARY"));
        AiTask queued = new AiTask(TASK_ID, DOC_ID, VERSION, "SUMMARY", "QUEUED", 5, null,
                "{}", "U1", 0, "ACTIVE:" + VERSION + ":SUMMARY:U1", null, 0);
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(queued));
        when(aiTaskRepository.cancelQueued(TASK_ID)).thenReturn(true);

        orchestrator.cancel(TASK_ID);

        verify(aiTaskRepository).cancelQueued(TASK_ID);
    }

    @Test
    void reusesActiveTaskInsteadOfCreatingNew() {
        // UT-08 活跃去重：同 (版本, 类型, 发起人) 存在 QUEUED/RUNNING → 返回既有 taskId
        login(Set.of("AI_SUMMARY"));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(readyDoc("U1")));
        givenReadyChunks();
        AiTask active = new AiTask(TASK_ID, DOC_ID, VERSION, "SUMMARY", "RUNNING", 5, null,
                "{}", "U1", 40, null, null, 0);
        when(aiTaskRepository.findActive(VERSION, "SUMMARY", "U1")).thenReturn(Optional.of(active));

        assertEquals(TASK_ID, orchestrator.createTask(DOC_ID, "SUMMARY"));
        verify(aiTaskRepository, never()).insertOrReuse(any());
        verify(outboxEventRepository, never()).publish(any(), any(), any(), any());
    }

    @Test
    void rejectsWhenVectorsNotReadyWith40901() {
        login(Set.of("AI_SUMMARY"));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(readyDoc("U1")));
        when(embeddingRepository.countNotReady(VERSION)).thenReturn(3);

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> orchestrator.createTask(DOC_ID, "SUMMARY"));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void rejectsWhenDocumentNotReadyWith40901() {
        login(Set.of("AI_SUMMARY"));
        Document parsing = readyDoc("U1");
        parsing.setStatus("PARSING");
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(parsing));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> orchestrator.createTask(DOC_ID, "SUMMARY"));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void rejectsMissingPermissionWith40301() {
        login(Set.of("AI_KEYPOINT")); // 缺 AI_SUMMARY

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> orchestrator.createTask(DOC_ID, "SUMMARY"));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
    }

    @Test
    void requiresDocumentReadInAdditionToAiPermission() {
        // 1105 审查 P2-3：DOCUMENT_READ 与 AI_SUMMARY 需同时满足（服务层组合校验）
        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"),
                Set.of("AI_SUMMARY"), Set.of())); // 仅 AI_SUMMARY，缺 DOCUMENT_READ
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(readyDoc("U1")));
        givenReadyChunks();
        when(aiTaskRepository.findActive(VERSION, "SUMMARY", "U1")).thenReturn(Optional.empty());

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> orchestrator.createTask(DOC_ID, "SUMMARY"));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
        verify(aiTaskRepository, never()).insertOrReuse(any());
    }

    @Test
    void otherUsersDocumentIsNotFound40401() {
        login(Set.of("AI_SUMMARY"));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(readyDoc("OTHER")));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> orchestrator.createTask(DOC_ID, "SUMMARY"));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }

    @Test
    void getTaskOfOtherRequesterIs40401() {
        login(Set.of("AI_SUMMARY"));
        AiTask foreign = new AiTask(TASK_ID, DOC_ID, VERSION, "SUMMARY", "QUEUED", 5, null,
                "{}", "OTHER", 0, null, null, 0);
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(foreign));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> orchestrator.getTask(TASK_ID));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }

    @Test
    void cancelQueuedSucceedsAndTerminalFails40901() {
        login(Set.of("AI_SUMMARY"));
        AiTask queued = new AiTask(TASK_ID, DOC_ID, VERSION, "SUMMARY", "QUEUED", 5, null,
                "{}", "U1", 0, null, null, 0);
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(queued));
        when(aiTaskRepository.cancelQueued(TASK_ID)).thenReturn(true);
        orchestrator.cancel(TASK_ID);
        verify(aiTaskRepository).cancelQueued(TASK_ID);

        AiTask succeeded = new AiTask(TASK_ID, DOC_ID, VERSION, "SUMMARY", "SUCCEEDED", 5, null,
                "{}", "U1", 100, null, null, 0);
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(succeeded));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> orchestrator.cancel(TASK_ID));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void cancelRunningUsesGuardedStatusTransition() {
        login(Set.of("AI_SUMMARY"));
        AiTask running = new AiTask(TASK_ID, DOC_ID, VERSION, "SUMMARY", "RUNNING", 5, null,
                "{}", "U1", 30, null, null, 0);
        when(aiTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(running));
        when(aiTaskRepository.cancelRunning(TASK_ID)).thenReturn(true);

        orchestrator.cancel(TASK_ID);

        verify(aiTaskRepository).cancelRunning(TASK_ID);
    }
}
