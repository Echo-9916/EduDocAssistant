package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.template.infrastructure.ConversionTask;
import com.cidas.edudoc.template.infrastructure.ConversionTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** 转换结果写事务（D-06）：结果对象登记与任务终态同事务；不生成文档版本（原文件不变）。 */
@Component
@RequiredArgsConstructor
public class ConversionTxWriter {

    private final OssObjectRepository ossObjectRepository;
    private final ConversionTaskRepository taskRepository;
    private final AuditService auditService;

    @Transactional
    public void succeeded(ConversionTask task, OssObject resultObject, String contentHash) {
        ossObjectRepository.insert(resultObject);
        taskRepository.updateSucceeded(task.getId(), resultObject.getId());
        auditService.record("CONVERSION_SUCCEEDED", "DOCUMENT", task.getDocumentId(), task.getDocumentId(),
                "200", Map.of("taskId", task.getId(), "targetFormat", task.getTargetFormat(),
                        "contentHash", contentHash));
    }

    @Transactional
    public void failed(String taskId, String code, String message, String documentId) {
        // 终审 I7：failure_code 列 VARCHAR2(60)，超长上游 code 同样会撑爆 FAILED 回写、卡死 RUNNING
        taskRepository.updateFailed(taskId, trim60(code), trim(message));
        if (documentId != null) {
            auditService.record("CONVERSION_FAILED", "DOCUMENT", documentId, documentId, "50001",
                    Map.of("taskId", taskId, "code", code));
        }
    }

    private static String trim(String value) {
        String text = value == null ? "转换失败" : value;
        return text.length() > 900 ? text.substring(0, 900) : text;
    }

    private static String trim60(String value) {
        String text = value == null || value.isBlank() ? "ENGINE_UNAVAILABLE" : value;
        return text.length() > 60 ? text.substring(0, 60) : text;
    }
}
