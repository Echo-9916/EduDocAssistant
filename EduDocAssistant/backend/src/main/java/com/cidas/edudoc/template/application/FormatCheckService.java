package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.PageResult;

/** 格式校验编排与查询（D-04，详细设计表 3-6 format-checks 接口）。 */
public interface FormatCheckService {

    CheckView create(String documentId);

    CheckView get(String checkId);

    /** 提交关联用：该文档版本最近一次已完成（PASSED/FAILED）的校验 ID；没有返回 null。 */
    String latestCompletedCheckId(String documentVersionId);

    /** 导出 Word 用：文档绑定模板版本优先，否则课程已发布模板；没有返回 null。 */
    String resolveTemplateVersionId(String documentId);

    PageResult<IssueView> issues(String checkId, String severity, Integer page, Integer pageSize);

    record CheckView(String id, String documentId, String documentVersionId, String templateVersionId,
                     String status, int totalIssueCount, int blockingIssueCount, String resultSummary) {
    }

    record IssueView(String id, String ruleId, String issueType, String locationType, String locationRef,
                     String expectedValue, String actualValue, String severity, String autoFixable,
                     String fixStatus, String guidance) {
    }
}
