package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.template.application.ConversionService.ConversionView;
import com.cidas.edudoc.template.application.impl.ConversionServiceImpl;
import com.cidas.edudoc.template.infrastructure.ConversionTask;
import com.cidas.edudoc.template.infrastructure.ConversionTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-06 转换任务：方向校验、QUEUED+事件、仅发起人可查、成功回执签名下载。 */
class ConversionServiceTest {

    private static final String DOC_ID = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String VER_ID = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String OBJ_ID = "C1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String TASK_ID = "D1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String RESULT_OBJ = "E1B2C3D4E5F60708090A0B0C0D0E0F10";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final ConversionTaskRepository taskRepository = mock(ConversionTaskRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final com.cidas.edudoc.infrastructure.oss.OssStorageAdapter ossStorageAdapter =
            mock(com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.class);
    private final ConversionService service = new ConversionServiceImpl(documentRepository, versionRepository,
            ossObjectRepository, taskRepository, outboxEventRepository, ossStorageAdapter);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void owner() {
        AuthContext.set(new UserPrincipal("U1", "u", "U", Set.of("STUDENT"), Set.of(), Set.of()));
    }

    private void docxReady() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "报告", "REPORT", "READY", VER_ID)));
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(
                new DocumentVersion(VER_ID, DOC_ID, 1, "UPLOAD", OBJ_ID, "U1", "hash", "A")));
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.docx", null, "etag", "hash", 100,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "NONE",
                "ORIGINAL")));
    }

    @Test
    void createsQueuedTaskAndPublishesEvent() {
        owner();
        docxReady();
        when(taskRepository.insert(any())).thenReturn(TASK_ID);

        ConversionView view = service.create(DOC_ID, "PDF");

        assertEquals("QUEUED", view.status());
        ArgumentCaptor<ConversionTask> captor = ArgumentCaptor.forClass(ConversionTask.class);
        verify(taskRepository).insert(captor.capture());
        assertEquals("PDF", captor.getValue().getTargetFormat());
        assertEquals(OBJ_ID, captor.getValue().getSourceObjectId());
        verify(outboxEventRepository).publish(eq("CONVERSION_TASK"), eq(TASK_ID),
                eq("CONVERSION_REQUESTED"), anyMap());
    }

    @Test
    void sameFormatDirectionRejected() {
        owner();
        docxReady();
        var e = assertThrows(CidasBusinessException.class, () -> service.create(DOC_ID, "DOCX"));
        assertEquals(40001, e.errorCode().code());
        verify(taskRepository, never()).insert(any());
    }

    @Test
    void badFormatParamRejected() {
        owner();
        docxReady();
        var e = assertThrows(CidasBusinessException.class, () -> service.create(DOC_ID, "exe"));
        assertEquals(40001, e.errorCode().code());
    }

    @Test
    void nonOwnerDocumentInvisible() {
        AuthContext.set(new UserPrincipal("U2", "v", "V", Set.of("STUDENT"), Set.of(), Set.of()));
        docxReady();
        var e = assertThrows(CidasBusinessException.class, () -> service.create(DOC_ID, "PDF"));
        assertEquals(40401, e.errorCode().code());
    }

    @Test
    void onlyRequesterCanQueryTask() {
        AuthContext.set(new UserPrincipal("U2", "v", "V", Set.of("STUDENT"), Set.of(), Set.of()));
        ConversionTask task = new ConversionTask();
        task.setId(TASK_ID);
        task.setRequestedBy("U1");
        task.setStatus("QUEUED");
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        var e = assertThrows(CidasBusinessException.class, () -> service.get(TASK_ID));
        assertEquals(40401, e.errorCode().code());
    }

    @Test
    void succeededTaskCarriesSignedDownloadUrl() {
        owner();
        ConversionTask task = new ConversionTask();
        task.setId(TASK_ID);
        task.setRequestedBy("U1");
        task.setStatus("SUCCEEDED");
        task.setResultObjectId(RESULT_OBJ);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(ossObjectRepository.findById(RESULT_OBJ)).thenReturn(Optional.of(new OssObject(
                RESULT_OBJ, "bucket", "cn-beijing", "edudoc/conversions/a.pdf", null, "etag", "hash", 50,
                "application/pdf", "NONE", "CONVERTED")));
        when(ossStorageAdapter.presignGet(anyString(), eq(900L))).thenReturn("https://oss/signed");

        ConversionView view = service.get(TASK_ID);

        assertEquals("https://oss/signed", view.downloadUrl());
    }

    @Test
    void listByDocumentOnlyOwnTasks() {
        owner();
        when(taskRepository.findByDocument(DOC_ID, "U1")).thenReturn(List.of());
        assertEquals(List.of(), service.listByDocument(DOC_ID));
    }

    /** 1107 P2-7：历史列表的 SUCCEEDED 行同样携带签名下载 URL。 */
    @Test
    void listByDocumentSignsSucceededRows() {
        owner();
        ConversionTask done = new ConversionTask();
        done.setId(TASK_ID);
        done.setDocumentId(DOC_ID);
        done.setStatus("SUCCEEDED");
        done.setResultObjectId(RESULT_OBJ);
        when(taskRepository.findByDocument(DOC_ID, "U1")).thenReturn(List.of(done));
        when(ossObjectRepository.findById(RESULT_OBJ)).thenReturn(Optional.of(new OssObject(
                RESULT_OBJ, "bucket", "cn-beijing", "edudoc/conversions/a.pdf", null, "etag", "hash",
                50, "application/pdf", "NONE", "CONVERTED")));
        when(ossStorageAdapter.presignGet("edudoc/conversions/a.pdf", 900L)).thenReturn("https://oss/signed2");

        var views = service.listByDocument(DOC_ID);

        assertEquals("https://oss/signed2", views.get(0).downloadUrl());
    }

    /** 格式视图 v3：SUCCEEDED 产物字节流预览（blob 方案的后端半边）。 */
    @Test
    void previewPdfStreamsSucceededArtifact() {
        owner();
        ConversionTask task = new ConversionTask();
        task.setId(TASK_ID);
        task.setDocumentId(DOC_ID);
        task.setRequestedBy("U1");
        task.setStatus("SUCCEEDED");
        task.setResultObjectId(RESULT_OBJ);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(ossObjectRepository.findById(RESULT_OBJ)).thenReturn(Optional.of(new OssObject(
                RESULT_OBJ, "bucket", "cn-beijing", "edudoc/conversions/a.pdf", null, "etag", "hash", 50,
                "application/pdf", "NONE", "CONVERTED")));
        when(ossStorageAdapter.readBytes("edudoc/conversions/a.pdf", 20L * 1024 * 1024))
                .thenReturn(new byte[]{'%', 'P', 'D', 'F'});

        assertEquals("%PDF", new String(service.previewPdf(TASK_ID), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void previewPdfRejectsTaskWithoutArtifact() {
        owner();
        ConversionTask task = new ConversionTask();
        task.setId(TASK_ID);
        task.setDocumentId(DOC_ID);
        task.setRequestedBy("U1");
        task.setStatus("RUNNING");
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        var e = assertThrows(com.cidas.edudoc.common.api.CidasBusinessException.class,
                () -> service.previewPdf(TASK_ID));
        assertEquals(40001, e.errorCode().code());
    }
}
