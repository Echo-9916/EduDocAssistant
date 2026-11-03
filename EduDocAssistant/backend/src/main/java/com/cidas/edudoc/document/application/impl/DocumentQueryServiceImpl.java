package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.DocumentQueryService;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentViewRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentQueryServiceImpl implements DocumentQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentRepository documentRepository;

    @Override
    public PageResult<Summary> list(ListQuery query) {
        String ownerId = AuthContext.require().userId();
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int pageSize = clampPageSize(query.pageSize());
        String keyword = escapeLike(query.keyword());
        long total = documentRepository.count(ownerId, query.courseId(), query.docType(),
                query.status(), keyword);
        List<Summary> items = total == 0 ? List.of()
                : documentRepository.page(ownerId, query.courseId(), query.docType(), query.status(),
                        keyword, (page - 1) * pageSize, pageSize).stream()
                .map(DocumentQueryServiceImpl::toSummary)
                .toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    public Detail detail(String documentId) {
        if (!com.cidas.edudoc.common.persistence.Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        String ownerId = AuthContext.require().userId();
        DocumentViewRow row = documentRepository.detail(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        // 非 owner 一律 40401，不泄露存在性（课程可见/ACL 属后续切片）
        if (!ownerId.equalsIgnoreCase(row.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return new Detail(row.getId(), row.getTitle(), row.getDocType(), row.getStatus(),
                row.getCourseId(), row.getCourseName(), row.getSizeBytes(), row.getMimeType(),
                row.getVersionNo(), row.getCurrentVersionId(), row.getContentHash(),
                row.getChunkCount(), row.getCreateTime(), row.getUpdateTime(),
                row.getDeletedAt(), row.getPurgeAt());
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
                row.getVersionNo(), row.getCreateTime(), row.getUpdateTime());
    }
}
