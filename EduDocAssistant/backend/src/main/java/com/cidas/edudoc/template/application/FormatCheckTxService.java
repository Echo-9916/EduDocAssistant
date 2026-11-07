package com.cidas.edudoc.template.application;

import com.cidas.edudoc.template.domain.IssueDraft;

import java.util.List;

/** 校验结果写事务（D-04）：问题行与任务回写同事务，审计不写正文。 */
public interface FormatCheckTxService {

    void writeResult(String checkId, List<IssueDraft> issues, int total, int blocking, String status,
                     String documentId, String versionId);

    void writeError(String checkId, String reason, String documentId, String versionId);
}
