package com.cidas.edudoc.template.application;

import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.ConvertEngineGateway.ConvertResult;
import com.cidas.edudoc.template.application.impl.ConversionTxWriter;
import com.cidas.edudoc.template.infrastructure.ConversionTask;
import com.cidas.edudoc.template.infrastructure.ConversionTaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-06 Worker：CAS 跳过、成功登记 CONVERTED 对象（不建版本）、引擎失败结构化落因。 */
class ConversionWorkerTest {

    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final ConvertEngineGateway engine = mock(ConvertEngineGateway.class);
    private final ConversionTaskRepository taskRepository = mock(ConversionTaskRepository.class);
    private final ConversionTxWriter txWriter = mock(ConversionTxWriter.class);

    private final ConversionWorker worker = new ConversionWorker(
            mock(org.springframework.data.redis.core.StringRedisTemplate.class),
            ossObjectRepository, ossStorageAdapter, engine, taskRepository, txWriter,
            new com.cidas.edudoc.template.infrastructure.ConversionProperties(false, 1,
                    "edudoc:stream:conversion", "cg-conversion", 120000),
            new OssProperties("endpoint", "cn-beijing", "cidas-oss-bucket", "ak", "sk", "arn", "sts",
                    "edudoc/uploads", 120, 900),
            new com.fasterxml.jackson.databind.ObjectMapper());

    private ConversionTask task() {
        ConversionTask task = new ConversionTask();
        task.setId("T1");
        task.setDocumentId("DOC1");
        task.setSourceVersionId("VER1");
        task.setSourceObjectId("OBJ1");
        task.setTargetFormat("PDF");
        task.setRequestedBy("U1");
        task.setStatus("QUEUED");
        return task;
    }

    private void readySource() throws Exception {
        Path temp = Files.createTempFile("conv-test", ".docx");
        Files.writeString(temp, "src");
        when(taskRepository.findById("T1")).thenReturn(Optional.of(task()));
        when(ossObjectRepository.findById("OBJ1")).thenReturn(Optional.of(new OssObject(
                "OBJ1", "cidas-oss-bucket", "cn-beijing", "edudoc/uploads/a.docx", null, "e", "h", 3,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "NONE",
                "ORIGINAL")));
        doAnswer(invocation -> {
            Files.copy(temp, invocation.getArgument(1), StandardCopyOption.REPLACE_EXISTING);
            return null;
        }).when(ossStorageAdapter).downloadTo(anyString(), any());
        when(ossStorageAdapter.putBytes(anyString(), any(), anyString()))
                .thenReturn(new OssStorageAdapter.ObjectInfo(9, "ETAG-R", null, "SSE-OSS"));
    }

    @Test
    void staleMessageSkippedByCas() throws Exception {
        when(taskRepository.markRunning("T1")).thenReturn(0);

        worker.process("T1");

        verify(ossStorageAdapter, never()).downloadTo(anyString(), any());
    }

    @Test
    void successPersistsConvertedObjectAndSucceeded() throws Exception {
        when(taskRepository.markRunning("T1")).thenReturn(1);
        readySource();
        when(engine.convert(any(), anyString(), eq("PDF")))
                .thenReturn(new ConvertResult("pdf-bytes".getBytes(), "application/pdf", "a.pdf"));

        worker.process("T1");

        ArgumentCaptor<OssObject> object = ArgumentCaptor.forClass(OssObject.class);
        verify(txWriter).succeeded(eq(task()), object.capture(), any());
        assertEquals("CONVERTED", object.getValue().getObjectRole());
        assertEquals(9, object.getValue().getSizeBytes());
    }

    @Test
    void engineFailureRecordsStructuredReason() throws Exception {
        when(taskRepository.markRunning("T1")).thenReturn(1);
        readySource();
        when(engine.convert(any(), anyString(), eq("PDF")))
                .thenThrow(new ConversionEngineException("ENCRYPTED", "PDF 已加密"));

        worker.process("T1");

        verify(txWriter).failed(eq("T1"), eq("ENCRYPTED"), eq("PDF 已加密"), eq("DOC1"));
    }

    /** 1106 审查 P2-3：引擎超时按计划落约定码 50401，而非自由文本 TIMEOUT。 */
    @Test
    void engineTimeoutRecordsAgreedCode50401() throws Exception {
        when(taskRepository.markRunning("T1")).thenReturn(1);
        readySource();
        when(engine.convert(any(), anyString(), eq("PDF")))
                .thenThrow(new com.cidas.edudoc.template.application.ConversionEngineException(
                        "TIMEOUT", "转换超时"));

        worker.process("T1");

        verify(txWriter).failed(eq("T1"), eq("50401"), eq("转换超时"), eq("DOC1"));
    }

    @Test
    void missingSourceRecordsEngineUnavailable() throws Exception {
        when(taskRepository.markRunning("T1")).thenReturn(1);
        when(taskRepository.findById("T1")).thenReturn(Optional.of(task()));
        when(ossObjectRepository.findById("OBJ1")).thenReturn(Optional.empty());

        worker.process("T1");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(txWriter).failed(eq("T1"), code.capture(), anyString(), eq("DOC1"));
        assertEquals("ENGINE_UNAVAILABLE", code.getValue());
    }
}
