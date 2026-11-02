package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.document.application.DocumentProvisioningService.CreatedDocument;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.ObjectInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B-04 建档事务内容断言：五写 + 审计 + Outbox 的字段与顺序。 */
class DocumentProvisioningServiceImplTest {

    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final OssProperties properties = new OssProperties("endpoint", "cn-beijing", "cidas-oss-bucket",
            "ak", "sk", "arn", "sts", "edudoc/uploads", 120, 900);
    private final DocumentProvisioningServiceImpl service = new DocumentProvisioningServiceImpl(
            ossObjectRepository, documentRepository, versionRepository, outboxEventRepository,
            auditService, properties);

    private static UploadSession session() {
        return new UploadSession("S1", "U1", "C1", "标题", com.cidas.edudoc.document.domain.DocumentType.COURSEWARE,
                "a.pdf", 1024, "application/pdf", "edudoc/uploads/202611/U1/S1-a.pdf",
                UploadSession.Status.COMPLETING, null, null, Instant.now());
    }

    @Test
    void provisionWritesFiveRowsAuditAndOutbox() {
        when(outboxEventRepository.publish(any(), any(), any(), any())).thenReturn("E1");

        CreatedDocument created = service.provision(session(),
                new ObjectInfo(1024, "ETAG", null, "AES256"), "abc123");

        ArgumentCaptor<OssObject> object = ArgumentCaptor.forClass(OssObject.class);
        verify(ossObjectRepository).insert(object.capture());
        assertEquals("cidas-oss-bucket", object.getValue().getBucketName());
        assertEquals("ORIGINAL", object.getValue().getObjectRole());
        assertEquals("AES256", object.getValue().getEncryptionType());
        assertEquals(32, object.getValue().getId().length());

        ArgumentCaptor<Document> document = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).insert(document.capture());
        assertEquals("PARSING", document.getValue().getStatus());
        assertEquals("C1", document.getValue().getCourseId());
        assertEquals("COURSEWARE", document.getValue().getDocType());
        assertEquals(document.getValue().getId(), created.documentId());

        ArgumentCaptor<DocumentVersion> version = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versionRepository).insert(version.capture());
        assertEquals(1, version.getValue().getVersionNo());
        assertEquals("UPLOAD", version.getValue().getVersionType());
        assertEquals(object.getValue().getId(), version.getValue().getSourceObjectId());
        assertEquals("abc123", version.getValue().getContentHash());
        assertEquals(version.getValue().getId(), created.versionId());
        verify(documentRepository).updateCurrentVersion(created.documentId(), created.versionId());

        verify(outboxEventRepository).publish(eq("DOCUMENT_VERSION"), eq(created.versionId()),
                eq("DOCUMENT_PARSE_REQUESTED"), any(Map.class));
        verify(auditService).record(eq("UPLOAD_COMPLETED"), eq("DOCUMENT"), eq(created.documentId()),
                eq(created.documentId()), eq("200"), any(Map.class));
    }

    @Test
    void provisionDefaultsEncryptionWhenAbsent() {
        service.provision(session(), new ObjectInfo(1024, "ETAG", null, null), "hash");
        ArgumentCaptor<OssObject> object = ArgumentCaptor.forClass(OssObject.class);
        verify(ossObjectRepository).insert(object.capture());
        assertEquals("NONE", object.getValue().getEncryptionType());
    }

    @Test
    void findByObjectKeyMapsVersionToIds() {
        OssObject object = new OssObject("OID", "cidas-oss-bucket", "cn-beijing", "k", null,
                "ETAG", "h", 1, "application/pdf", "NONE", "ORIGINAL");
        when(ossObjectRepository.findByObject("cidas-oss-bucket", "k")).thenReturn(Optional.of(object));
        when(versionRepository.findBySourceObject("OID")).thenReturn(Optional.of(
                new DocumentVersion("VID", "DOCID", 1, "UPLOAD", "OID", "U1", "h", "A")));

        Optional<CreatedDocument> found = service.findByObjectKey("k");

        assertTrue(found.isPresent());
        assertEquals("DOCID", found.get().documentId());
        assertEquals("VID", found.get().versionId());
    }

    @Test
    void findByObjectKeyEmptyWhenObjectMissing() {
        when(ossObjectRepository.findByObject(anyString(), anyString())).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), service.findByObjectKey("k"));
    }
}
