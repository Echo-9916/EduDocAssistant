package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.template.application.FormatCheckService;
import com.cidas.edudoc.template.infrastructure.FormatCheck;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssue;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 格式校验编排（D-04）：模板选择口径（P4：文档绑定版本优先，否则课程当前已发布版本，J1：非 docx 落 ERROR）
 * → QUEUED + Outbox；查询复用文档可见性（非 owner 40401，同 02 口径）。
 */
@Service
@RequiredArgsConstructor
public class FormatCheckServiceImpl implements FormatCheckService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final OssObjectRepository ossObjectRepository;
    private final FormatCheckRepository checkRepository;
    private final FormatIssueRepository issueRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public CheckView create(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        Document document = loadOwnedDocument(documentId);
        if (!"READY".equals(document.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档尚未解析就绪");
        }
        String versionId = document.getCurrentVersionId();
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "文档当前版本不存在"));

        OssObject object = version.getSourceObjectId() == null ? null
                : ossObjectRepository.findById(version.getSourceObjectId()).orElse(null);
        boolean docx = object != null && DOCX_MIME.equals(object.getMimeType());

        // 终审 Minor：非 docx 且无模板时提示两个事实，不只报模板误导 J1 语义
        String templateVersionId;
        try {
            templateVersionId = resolveTemplate(document);
        } catch (CidasBusinessException e) {
            if (!docx) {
                throw new CidasBusinessException(ErrorCode.CONFLICT,
                        "文档不是 Word(.docx) 格式，且课程尚未发布模板，无法发起格式校验");
            }
            throw e;
        }
        if (!docx) {
            // J1 拍板：非 docx 任务直接落 ERROR（模板版本必填，原因写摘要），不投事件
            String summary = "当前版本不是 Word(.docx) 文档，暂不支持排版格式校验（PDF 请提交原件并按模板自查）";
            FormatCheck error = queued(document, versionId, templateVersionId, "ERROR", summary);
            error.setId(checkRepository.insertError(error));
            return toView(error);
        }

        String requestedBy = AuthContext.require().userId();
        // 1106 审查 P3-1：文档行锁使「查活跃+插入」原子（并发双幂等键不再产两任务），锁随本事务释放
        documentRepository.lockForUpdate(document.getId());
        var active = checkRepository.findActive(versionId, templateVersionId, requestedBy);
        if (active.isPresent()) {
            return toView(active.get());
        }
        FormatCheck check = queued(document, versionId, templateVersionId, "QUEUED", null);
        String checkId = checkRepository.insert(check);
        check.setId(checkId);
        outboxEventRepository.publish("FORMAT_CHECK", checkId, "FORMAT_CHECK_REQUESTED", Map.of(
                "schemaVersion", 1,
                "checkId", checkId,
                "documentId", document.getId(),
                "versionId", versionId,
                "templateVersionId", templateVersionId,
                "sourceObjectId", version.getSourceObjectId()));
        return toView(check);
    }

    @Override
    public CheckView get(String checkId) {
        if (!Ids.isHex16(checkId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "校验任务 ID 格式不合法");
        }
        FormatCheck check = requireCheck(checkId);
        loadOwnedDocument(check.getDocumentId());
        return toView(check);
    }

    @Override
    public PageResult<IssueView> issues(String checkId, String severity, Integer page, Integer pageSize) {
        get(checkId);
        int pageNo = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = issueRepository.count(checkId, severity);
        List<IssueView> items = total == 0 ? List.of()
                : issueRepository.page(checkId, severity, (long) (pageNo - 1) * size, size).stream()
                .map(FormatCheckServiceImpl::toView)
                .toList();
        return new PageResult<>(items, total, pageNo, size);
    }

    private String resolveTemplate(Document document) {
        String bound = checkRepository.selectDocumentTemplateBinding(document.getId());
        if (bound != null) {
            return bound;
        }
        String courseCurrent = document.getCourseId() == null ? null
                : checkRepository.selectCoursePublishedTemplate(document.getCourseId());
        if (courseCurrent == null) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "课程尚未发布模板，无法发起格式校验");
        }
        return courseCurrent;
    }

    private FormatCheck requireCheck(String checkId) {
        return checkRepository.findById(checkId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "校验任务不存在"));
    }

    private Document loadOwnedDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!AuthContext.require().userId().equalsIgnoreCase(document.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private FormatCheck queued(Document document, String versionId, String templateVersionId,
                               String status, String summary) {
        FormatCheck check = new FormatCheck();
        check.setDocumentId(document.getId());
        check.setDocumentVersionId(versionId);
        check.setTemplateVersionId(templateVersionId);
        check.setRequestedBy(AuthContext.require().userId());
        check.setStatus(status);
        check.setResultSummary(summary);
        return check;
    }

    private static CheckView toView(FormatCheck check) {
        return new CheckView(check.getId(), check.getDocumentId(), check.getDocumentVersionId(),
                check.getTemplateVersionId(), check.getStatus(), check.getTotalIssueCount(),
                check.getBlockingIssueCount(), check.getResultSummary());
    }

    private static IssueView toView(FormatIssue issue) {
        return new IssueView(issue.getId(), issue.getRuleId(), issue.getIssueType(), issue.getLocationType(),
                issue.getLocationRef(), issue.getExpectedValue(), issue.getActualValue(), issue.getSeverity(),
                issue.getAutoFixable(), issue.getFixStatus(), issue.getGuidance());
    }
}
