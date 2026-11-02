package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.document.application.DocumentProvisioningService;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.ObjectInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentProvisioningServiceImpl implements DocumentProvisioningService {

    private final OssObjectRepository ossObjectRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditService auditService;
    private final OssProperties ossProperties;

    @Override
    @Transactional
    public CreatedDocument provision(UploadSession session, ObjectInfo objectInfo, String contentHash) {
        String ossId = Ids.hex16();
        ossObjectRepository.insert(new OssObject(ossId, ossProperties.bucket(), ossProperties.region(),
                session.objectKey(), objectInfo.versionId(), objectInfo.etag(), contentHash,
                objectInfo.contentLength(), session.mimeType(),
                objectInfo.encryption() == null ? "NONE" : objectInfo.encryption(), "ORIGINAL"));

        String documentId = Ids.hex16();
        documentRepository.insert(new Document(documentId, session.courseId(), session.ownerUserId(),
                session.title(), session.documentType().name(), "PARSING"));

        String versionId = Ids.hex16();
        documentVersionRepository.insert(new DocumentVersion(versionId, documentId, 1, "UPLOAD",
                ossId, session.ownerUserId(), contentHash, "A"));
        // current_version_id 与 version.document_id 循环引用，无 FK，同事务写入（数据库设计说明书 §3.4.8）
        documentRepository.updateCurrentVersion(documentId, versionId);

        outboxEventRepository.publish("DOCUMENT_VERSION", versionId, "DOCUMENT_PARSE_REQUESTED", Map.of(
                "schemaVersion", 1,
                "documentId", documentId,
                "versionId", versionId,
                "bucket", ossProperties.bucket(),
                "objectKey", session.objectKey()));

        auditService.record("UPLOAD_COMPLETED", "DOCUMENT", documentId, documentId, "200",
                Map.of("contentHash", contentHash, "sessionId", session.id()));
        return new CreatedDocument(documentId, versionId);
    }

    @Override
    public Optional<CreatedDocument> findByObjectKey(String objectKey) {
        return ossObjectRepository.findByObject(ossProperties.bucket(), objectKey)
                .flatMap(object -> documentVersionRepository.findBySourceObject(object.getId()))
                .map(version -> new CreatedDocument(version.getDocumentId(), version.getId()));
    }
}
