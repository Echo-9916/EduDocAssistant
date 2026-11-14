package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.application.DocumentQueryService;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRow;
import com.cidas.edudoc.document.infrastructure.DocumentViewRow;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentQueryServiceImpl implements DocumentQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final long VERSION_URL_TTL_SECONDS = 900;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final OssObjectRepository ossObjectRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final CollaborationService collaborationService;

    @Override
    public PageResult<Summary> list(ListQuery query) {
        String ownerId = AuthContext.require().userId();
        // 1103 审查 S2：HEXTORAW 前在入口拒绝非 hex，杜绝 Oracle 异常塌缩为 500
        if (query.courseId() != null && !query.courseId().isBlank() && !Ids.isHex16(query.courseId())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "courseId 格式不合法");
        }
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int pageSize = clampPageSize(query.pageSize());
        // 1103 审查 S3：offset 用 long 计算，超大页码不再溢出回退首页
        long offset = (long) (page - 1) * pageSize;
        String keyword = escapeLike(query.keyword());
        // 回收站保持 owner-only；其余列表同时包含活跃协作文档
        boolean includeCollab = !"DELETED".equals(query.status());
        long total = documentRepository.count(ownerId, query.courseId(), query.docType(),
                query.status(), keyword, includeCollab);
        List<Summary> items = total == 0 ? List.of()
                : documentRepository.page(ownerId, query.courseId(), query.docType(), query.status(),
                        keyword, includeCollab, offset, pageSize).stream()
                .map(DocumentQueryServiceImpl::toSummary)
                .toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    public Detail detail(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        String ownerId = AuthContext.require().userId();
        DocumentViewRow row = documentRepository.detail(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        // 非 owner 且非协作者一律 40401，不泄露存在性（课程可见/ACL 属后续切片）
        if (!ownerId.equalsIgnoreCase(row.getOwnerId())
                && !collaborationService.isCollaborator(documentId, ownerId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return new Detail(row.getId(), row.getTitle(), row.getDocType(), row.getStatus(),
                row.getCourseId(), row.getCourseName(), row.getSizeBytes(), row.getMimeType(),
                row.getVersionNo(), row.getCurrentVersionId(), row.getContentHash(),
                row.getChunkCount(), row.getCreateTime(), row.getUpdateTime(),
                row.getDeletedAt(), row.getPurgeAt());
    }

    @Override
    public List<VersionView> listVersions(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        String ownerId = AuthContext.require().userId();
        DocumentViewRow row = documentRepository.detail(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!ownerId.equalsIgnoreCase(row.getOwnerId())
                && !collaborationService.isCollaborator(documentId, ownerId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return versionRepository.listByDocument(documentId).stream()
                .map(v -> new VersionView(v.getVersionId(), v.getVersionNo(), v.getVersionType(),
                        v.getCreateTime(), v.getChangeSummary(), presignSource(v.getSourceObjectId(), false),
                        presignSource(v.getSourceObjectId(), true),
                        v.getVersionId().equalsIgnoreCase(row.getCurrentVersionId())))
                .toList();
    }

    @Override
    public VersionContent versionContent(String documentId, String versionId) {
        if (!Ids.isHex16(documentId) || !Ids.isHex16(versionId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "ID 格式不合法");
        }
        String ownerId = AuthContext.require().userId();
        DocumentViewRow row = documentRepository.detail(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!ownerId.equalsIgnoreCase(row.getOwnerId())
                && !collaborationService.isCollaborator(documentId, ownerId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        com.cidas.edudoc.document.infrastructure.DocumentVersion version =
                versionRepository.findById(versionId)
                        .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "版本不存在"));
        if (!version.getDocumentId().equalsIgnoreCase(documentId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        byte[] bytes = ossObjectRepository.findById(version.getSourceObjectId())
                .map(obj -> ossStorageAdapter.readBytes(obj.getObjectKey(), 20L * 1024 * 1024))
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "版本对象缺失，请重新上传", true, Map.of()));
        return new VersionContent(bytes, row.getMimeType() == null ? "application/octet-stream" : row.getMimeType());
    }

    private String presignSource(String sourceObjectIdHex, boolean inline) {
        if (sourceObjectIdHex == null) {
            return null;
        }
        return ossObjectRepository.findById(sourceObjectIdHex)
                .map(obj -> inline
                        ? ossStorageAdapter.presignGetInline(obj.getObjectKey(), VERSION_URL_TTL_SECONDS)
                        : ossStorageAdapter.presignGet(obj.getObjectKey(), VERSION_URL_TTL_SECONDS))
                .orElse(null);
    }

    private static int clampPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** LIKE 元字符转义（ESCAPE '\'）；未提供关键字返回 null。 */
    private static String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Summary toSummary(DocumentViewRow row) {
        return new Summary(row.getId(), row.getTitle(), row.getDocType(), row.getStatus(),
                row.getCourseId(), row.getCourseName(), row.getSizeBytes(), row.getMimeType(),
                row.getVersionNo(), row.getCreateTime(), row.getUpdateTime(),
                row.getOwnerName(), row.getRelation());
    }
}
