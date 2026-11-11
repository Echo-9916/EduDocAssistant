package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.message.application.NotificationEvents;
import com.cidas.edudoc.template.application.FormatCheckTxService;
import com.cidas.edudoc.template.domain.IssueDraft;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 校验结果单事务回写（D-04）：RUNNING 守卫更新，审计与业务同事务（1105 P1-2 口径）。 */
@Service
@RequiredArgsConstructor
public class FormatCheckTxServiceImpl implements FormatCheckTxService {

    private final FormatCheckRepository checkRepository;
    private final FormatIssueRepository issueRepository;
    private final AuditService auditService;
    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public void writeResult(String checkId, List<IssueDraft> issues, int total, int blocking, String status,
                            String documentId, String versionId) {
        if (checkRepository.updateResult(checkId, total, blocking, status,
                summarize(total, blocking)) == 0) {
            return;
        }
        issueRepository.insertDrafts(checkId, issues);
        // 终审 Minor：审计动作统一 FORMAT_CHECK_DONE（计划 D-04 口径），结论走 resultCode
        auditService.record("FORMAT_CHECK_DONE", "FORMAT_CHECK", checkId, documentId,
                status.equals("PASSED") ? "200" : "42202",
                Map.of("status", status, "totalIssueCount", total, "blockingIssueCount", blocking,
                        "versionId", versionId));
        // 2-5 生产者③（设计 §4.1）：结论 BLOCKER>0 → 文档 owner 收 FORMAT 消息，与结论回写、审计同事务。
        // 挂在 RUNNING 守卫之后：迟到/重复投递进不来，重新校验是新 checkId 会有新去重键（该再提醒）。
        if (blocking > 0) {
            String ownerId = checkRepository.selectDocumentOwner(documentId);
            if (ownerId != null) {
                outboxEventRepository.publish("FORMAT_CHECK", checkId, NotificationEvents.EVENT_TYPE,
                        NotificationEvents.payload(NotificationEvents.TYPE_FORMAT, checkId, null,
                                "DOCUMENT", documentId, null, List.of(ownerId),
                                "格式校验未通过",
                                "文档格式校验发现 " + blocking + " 项阻塞问题，请修复后重新校验。"));
            }
        }
    }

    @Override
    @Transactional
    public void writeError(String checkId, String reason, String documentId, String versionId) {
        if (checkRepository.updateError(checkId, trim(reason)) == 0) {
            return;
        }
        auditService.record("FORMAT_CHECK_DONE", "FORMAT_CHECK", checkId, documentId, "50001",
                Map.of("status", "ERROR", "reason", trim(reason), "versionId", versionId));
    }

    private static String summarize(int total, int blocking) {
        return blocking == 0
                ? (total == 0 ? "校验通过，未发现问题" : "校验通过，存在 " + total + " 个非阻断问题")
                : "校验未通过：" + total + " 个问题，其中阻断 " + blocking + " 个";
    }

    private static String trim(String value) {
        String text = value == null ? "未知错误" : value;
        return text.length() > 1900 ? text.substring(0, 1900) : text;
    }
}
