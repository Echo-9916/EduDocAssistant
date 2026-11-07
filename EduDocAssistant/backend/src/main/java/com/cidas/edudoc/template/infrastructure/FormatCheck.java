package com.cidas.edudoc.template.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 格式校验任务持久化对象（edudoc_format_check，状态 QUEUED/RUNNING/PASSED/FAILED/ERROR）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormatCheck {

    private String id;
    private String documentId;
    private String documentVersionId;
    private String templateVersionId;
    private String requestedBy;
    private String status;
    private int totalIssueCount;
    private int blockingIssueCount;
    private String startedAt;
    private String finishedAt;
    private String resultSummary;
}
