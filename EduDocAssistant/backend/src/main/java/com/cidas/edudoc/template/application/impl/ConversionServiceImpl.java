package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.outbox.OutboxEventRepository;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.ConversionService;
import com.cidas.edudoc.template.infrastructure.ConversionTask;
import com.cidas.edudoc.template.infrastructure.ConversionTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 转换编排（D-06）：仅对当前版本源对象发起（docx↔pdf 双向，同格式 40001）；
 * 结果不建版本不改 current_version（需求 §5.3.3.3「原文件保持不变」）；GET 仅发起人（40401）。
 */
@Service
@RequiredArgsConstructor
public class ConversionServiceImpl implements ConversionService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final OssObjectRepository ossObjectRepository;
    private final ConversionTaskRepository taskRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OssStorageAdapter ossStorageAdapter;

    @Override
    @Transactional
    public ConversionView create(String documentId, String targetFormat) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        if (!"PDF".equals(targetFormat) && !"DOCX".equals(targetFormat)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "目标格式仅支持 PDF / DOCX");
        }
        Document document = loadOwnedDocument(documentId);
        if (!"READY".equals(document.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档尚未解析就绪");
        }
        DocumentVersion version = versionRepository.findById(document.getCurrentVersionId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "文档当前版本不存在"));
        OssObject source = version.getSourceObjectId() == null ? null
                : ossObjectRepository.findById(version.getSourceObjectId()).orElse(null);
        if (source == null) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档源对象不存在");
        }
        boolean sourceDocx = DOCX_MIME.equals(source.getMimeType());
        boolean sourcePdf = "application/pdf".equals(source.getMimeType());
        if (!sourceDocx && !sourcePdf) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "当前版本源文件不支持转换（仅 Word/PDF）");
        }
        if ((sourceDocx && "DOCX".equals(targetFormat)) || (sourcePdf && "PDF".equals(targetFormat))) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档已是目标格式，无需转换");
        }
        ConversionTask task = new ConversionTask();
        task.setDocumentId(documentId);
        task.setSourceVersionId(version.getId());
        task.setSourceObjectId(source.getId());
        task.setTargetFormat(targetFormat);
        task.setStatus("QUEUED");
        task.setRequestedBy(AuthContext.require().userId());
        String taskId = taskRepository.insert(task);
        outboxEventRepository.publish("CONVERSION_TASK", taskId, "CONVERSION_REQUESTED", Map.of(
                "schemaVersion", 1, "taskId", taskId, "documentId", documentId));
        return toView(taskWithId(taskId, task), null, null);
    }

    @Override
    public ConversionView get(String taskId) {
        if (!Ids.isHex16(taskId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "任务 ID 格式不合法");
        }
        ConversionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "转换任务不存在"));
        if (!task.getRequestedBy().equalsIgnoreCase(AuthContext.require().userId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "转换任务不存在");
        }
        return toView(task, signedUrlIfSucceeded(task), previewUrlIfSucceeded(task));
    }

    @Override
    public List<ConversionView> listByDocument(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        // 记录列表按「发起人=当前用户」过滤即可自证可见性，不再重复文档归属查询
        // 1107 P2-7：SUCCEEDED 记录逐行签名（历史页可直接下载，不再显示"链接已过期"）
        return taskRepository.findByDocument(documentId, AuthContext.require().userId()).stream()
                .map(task -> toView(task, signedUrlIfSucceeded(task), previewUrlIfSucceeded(task)))
                .toList();
    }

    @Override
    public byte[] previewPdf(String taskId) {
        if (!Ids.isHex16(taskId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "任务 ID 格式不合法");
        }
        ConversionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "转换任务不存在"));
        if (!task.getRequestedBy().equalsIgnoreCase(AuthContext.require().userId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "转换任务不存在");
        }
        if (!"SUCCEEDED".equals(task.getStatus()) || task.getResultObjectId() == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "任务尚无成功的转换产物可预览");
        }
        return ossObjectRepository.findById(task.getResultObjectId())
                .map(object -> ossStorageAdapter.readBytes(object.getObjectKey(), 20L * 1024 * 1024))
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "预览对象缺失，请重新发起转换", true, Map.of()));
    }

    private String signedUrlIfSucceeded(ConversionTask task) {
        if (!"SUCCEEDED".equals(task.getStatus()) || task.getResultObjectId() == null) {
            return null;
        }
        return ossObjectRepository.findById(task.getResultObjectId())
                .map(object -> ossStorageAdapter.presignGet(object.getObjectKey(), 900L))
                .orElse(null);
    }

    /** 内联预览签名（iframe 显示不弹下载），与下载链同对象不同 disposition。 */
    private String previewUrlIfSucceeded(ConversionTask task) {
        if (!"SUCCEEDED".equals(task.getStatus()) || task.getResultObjectId() == null) {
            return null;
        }
        return ossObjectRepository.findById(task.getResultObjectId())
                .map(object -> ossStorageAdapter.presignGetInline(object.getObjectKey(), 900L))
                .orElse(null);
    }

    private Document loadOwnedDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!AuthContext.require().userId().equalsIgnoreCase(document.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private static ConversionTask taskWithId(String taskId, ConversionTask task) {
        task.setId(taskId);
        return task;
    }

    private static ConversionView toView(ConversionTask task, String downloadUrl, String previewUrl) {
        return new ConversionView(task.getId(), task.getDocumentId(), task.getTargetFormat(),
                task.getStatus(), task.getProgress(), task.getFailureCode(), task.getFailureMessage(),
                downloadUrl, previewUrl, task.getStartedAt(), task.getFinishedAt());
    }
}
