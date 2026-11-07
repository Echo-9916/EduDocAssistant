package com.cidas.edudoc.template.application;

import java.util.List;

/** 一键修复（D-05，详细设计表 3-6 fix 接口 + §3.3.2 autoFix 伪代码）。 */
public interface FormatFixService {

    String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    FixView fix(String checkId, List<String> issueIds);

    record FixView(String newVersionId, Integer newVersionNo, String recheckId,
                   List<String> fixedIssueIds, List<String> skippedIssueIds) {
    }
}
