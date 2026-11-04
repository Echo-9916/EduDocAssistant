package com.cidas.edudoc.ai.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 润色结果持久化对象（edudoc_polish_result，decision PENDING/ACCEPT/PARTIAL/REJECT；撤销回拨 PENDING，审计 POLISH_REVERTED 记史）。 */
@Data
@AllArgsConstructor
public class PolishResult {

    private String id;
    private String documentId;
    private String documentVersionId;
    private String userId;
    private String aiTaskId;
    private String sourceText;
    private String polishedText;
    private String acceptedText;
    private String decision;
}
