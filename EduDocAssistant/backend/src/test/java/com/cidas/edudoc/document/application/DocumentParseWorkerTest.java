package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.Chunk;
import com.cidas.edudoc.document.domain.TextBlock;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.ParseProperties;
import com.cidas.edudoc.document.infrastructure.TikaTextExtractor;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B-06 worker：状态去重、失败回写 FAILED、ACK 行为。 */
class DocumentParseWorkerTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final OssStorageAdapter adapter = mock(OssStorageAdapter.class);
    private final TikaTextExtractor extractor = mock(TikaTextExtractor.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentParseTxService txService = mock(DocumentParseTxService.class);
    private final ParseProperties properties =
            new ParseProperties(true, 1, "edudoc:stream:document-parse", "cg-document-parse", 1500);
    private final DocumentParseWorker worker = new DocumentParseWorker(redis, adapter, extractor,
            documentRepository, txService, properties, new ObjectMapper());

    private static MapRecord<String, Object, Object> record(String documentId, String versionId) {
        String payload = "{\"schemaVersion\":1,\"documentId\":\"" + documentId + "\",\"versionId\":\""
                + versionId + "\",\"bucket\":\"b\",\"objectKey\":\"edudoc/uploads/a.pdf\"}";
        Map<Object, Object> fields = Map.of("payload", payload, "aggregateRef", versionId,
                "eventType", "DOCUMENT_PARSE_REQUESTED");
        return MapRecord.create("edudoc:stream:document-parse", fields)
                .withId(org.springframework.data.redis.connection.stream.RecordId.of("1-0"));
    }

    private static com.cidas.edudoc.document.infrastructure.Document doc(String status, String currentVersionId) {
        return new com.cidas.edudoc.document.infrastructure.Document(
                "D1", null, "U1", "t", "COURSEWARE", status, currentVersionId);
    }

    private static final Consumer CONSUMER = Consumer.from("cg-document-parse", "c1");

    @Test
    void parsesWhenDocumentIsParsingAndVersionMatches() throws Exception {
        when(documentRepository.findById("D1")).thenReturn(Optional.of(doc("PARSING", "V1")));
        when(extractor.extract(any(), anyString()))
                .thenReturn(List.of(new TextBlock("hello world text", "H1", 1)));

        worker.handle(null, CONSUMER, record("D1", "V1"));

        verify(txService).writeSuccess(eq("D1"), eq("V1"),
                org.mockito.ArgumentMatchers.argThat(chunks -> chunks.size() == 1
                        && chunks.get(0).text().contains("hello world text")));
    }

    @Test
    void skipsStaleEventWhenAlreadyReady() throws Exception {
        when(documentRepository.findById("D1")).thenReturn(Optional.of(doc("READY", "V1")));

        worker.handle(null, CONSUMER, record("D1", "V1"));

        verify(txService, never()).writeSuccess(any(), any(), any());
        verify(txService, never()).writeFailure(any(), any(), any());
    }

    @Test
    void skipsEventForNonCurrentVersion() throws Exception {
        when(documentRepository.findById("D1")).thenReturn(Optional.of(doc("PARSING", "V2")));

        worker.handle(null, CONSUMER, record("D1", "V1"));

        verify(txService, never()).writeSuccess(any(), any(), any());
    }

    @Test
    void extractionFailureMarksDocumentFailed() throws Exception {
        when(documentRepository.findById("D1")).thenReturn(Optional.of(doc("PARSING", "V1")));
        when(extractor.extract(any(), anyString())).thenThrow(new java.io.IOException("Tika 失败"));

        worker.handle(null, CONSUMER, record("D1", "V1"));

        verify(txService).writeFailure(eq("D1"), eq("V1"), anyString());
    }

    @Test
    void emptyDocumentMarksFailed() throws Exception {
        when(documentRepository.findById("D1")).thenReturn(Optional.of(doc("PARSING", "V1")));
        when(extractor.extract(any(), anyString())).thenReturn(List.of());

        worker.handle(null, CONSUMER, record("D1", "V1"));

        verify(txService).writeFailure(eq("D1"), eq("V1"), anyString());
    }
}
