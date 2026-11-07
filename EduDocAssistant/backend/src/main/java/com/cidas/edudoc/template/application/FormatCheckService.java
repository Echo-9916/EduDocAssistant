package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.PageResult;

/** 格式校验编排与查询（D-04，详细设计表 3-6 format-checks 接口）。 */
public interface FormatCheckService {

    CheckView create(String documentId);

    CheckView get(String checkId);

    PageResult<IssueView> issues(String checkId, String severity, Integer page, Integer pageSize);

    record CheckView(String id, String documentId, String documentVersionId, String templateVersionId,
                     String status, int totalIssueCount, int blockingIssueCount, String resultSummary) {
    }

    record IssueView(String id, String ruleId, String issueType, String locationType, String locationRef,
                     String expectedValue, String actualValue, String severity, String autoFixable,
                     String fixStatus, String guidance) {
    }
}
