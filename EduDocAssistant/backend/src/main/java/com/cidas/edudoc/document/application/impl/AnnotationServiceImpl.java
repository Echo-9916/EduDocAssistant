package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.AnnotationService;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentAnnotationRepository;
import com.cidas.edudoc.document.infrastructure.DocumentAnnotationRow;
import com.cidas.edudoc.document.infrastructure.DocumentChunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 批注实现：读权限 = owner 或协作者；位置沿用 chunk 的闭区间字符偏移。 */
@Service
@RequiredArgsConstructor
public class AnnotationServiceImpl implements AnnotationService {

    private static final Set<String> STATUSES = Set.of("O", "R");
    private static final int CONTENT_MAX = 2000;

    private final CollaborationService collaborationService;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentAnnotationRepository annotationRepository;
    private final AuditService auditService;

    @Override
    public List<AnnotationView> list(String documentId, String status) {
        Document doc = readableDocument(documentId);
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (filter != null && !STATUSES.contains(filter)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "状态仅支持 O（待处理）或 R（已解决）");
        }
        return annotationRepository.listByDocument(doc.getId(), filter).stream()
                .map(row -> toView(row, doc)).toList();
    }

    @Override
    @Transactional
    public AnnotationView create(String documentId, CreateCommand command) {
        Document doc = readableDocument(documentId);
        if (command == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "批注参数不能为空");
        }
        String versionId = command.versionId() == null || command.versionId().isBlank()
                ? doc.getCurrentVersionId() : command.versionId().trim();
        if (!Ids.isHex16(versionId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "版本 ID 格式不合法");
        }
        DocumentVersion version = versionRepository.findById(versionId)
                .filter(v -> v.getDocumentId().equalsIgnoreCase(doc.getId()))
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "版本不存在"));
        Integer start = command.charStart();
        Integer end = command.charEnd();
        if (start == null || end == null || start < 0 || end < start) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "批注区间不合法");
        }
        String content = command.content() == null ? "" : command.content().trim();
        if (content.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "批注内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "批注内容不能超过 2000 字");
        }
        List<DocumentChunk> chunks = chunkRepository.selectAllByVersion(version.getId());
        if (chunks.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "该版本暂无可读文本，无法批注");
        }
        // 区间必须落在某个 chunk 的闭区间内：与引用回跳共用同一字符偏移口径
        boolean covered = chunks.stream().anyMatch(c -> c.getCharStart() <= end && c.getCharEnd() >= start);
        if (!covered) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "批注区间不在可读文本范围内");
        }
        String annotationId = annotationRepository.create(doc.getId(), version.getId(),
                AuthContext.require().userId(), start, end, content);
        auditService.record("ANNOTATION_CREATED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("annotationId", annotationId, "versionId", version.getId(),
                        "charStart", start, "charEnd", end));
        return toView(annotationRepository.findById(annotationId), doc);
    }

    @Override
    @Transactional
    public AnnotationView resolve(String annotationId, boolean resolved) {
        DocumentAnnotationRow row = requireAnnotation(annotationId);
        Document doc = readableDocument(row.getDocumentId());
        String userId = AuthContext.require().userId();
        requireOwnerOrAuthor(doc, row, userId, "仅文档所有者或批注作者可解决批注");
        if (resolved) {
            annotationRepository.resolve(row.getId(), userId);
        } else {
            annotationRepository.reopen(row.getId());
        }
        auditService.record(resolved ? "ANNOTATION_RESOLVED" : "ANNOTATION_REOPENED",
                "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("annotationId", row.getId()));
        return toView(annotationRepository.findById(row.getId()), doc);
    }

    @Override
    @Transactional
    public void delete(String annotationId) {
        DocumentAnnotationRow row = requireAnnotation(annotationId);
        Document doc = readableDocument(row.getDocumentId());
        String userId = AuthContext.require().userId();
        // owner 也不能删他人批注：删除权只归作者本人
        if (!userId.equalsIgnoreCase(row.getAuthorId())) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "仅批注作者本人可删除批注");
        }
        annotationRepository.delete(row.getId());
        auditService.record("ANNOTATION_DELETED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("annotationId", row.getId()));
    }

    private Document readableDocument(String documentId) {
        collaborationService.requireReadable(documentId);
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
    }

    private DocumentAnnotationRow requireAnnotation(String annotationId) {
        if (!Ids.isHex16(annotationId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "批注不存在");
        }
        DocumentAnnotationRow row = annotationRepository.findById(annotationId);
        if (row == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "批注不存在");
        }
        return row;
    }

    private static AnnotationView toView(DocumentAnnotationRow row, Document doc) {
        String userId = AuthContext.require().userId();
        boolean isAuthor = userId.equalsIgnoreCase(row.getAuthorId());
        boolean isOwner = userId.equalsIgnoreCase(doc.getOwnerId());
        return new AnnotationView(row.getId(), row.getDocumentId(), row.getDocumentVersionId(),
                row.getAuthorId(), row.getAuthorName(), row.getAuthorAccount(),
                row.getCharStart(), row.getCharEnd(), row.getContent(), row.getStatus(),
                row.getResolvedBy(), row.getResolvedByName(), row.getResolvedTime(),
                row.getCreateTime(), isAuthor, isAuthor || isOwner);
    }

    private static void requireOwnerOrAuthor(Document doc, DocumentAnnotationRow row, String userId,
                                             String message) {
        if (!userId.equalsIgnoreCase(doc.getOwnerId()) && !userId.equalsIgnoreCase(row.getAuthorId())) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}