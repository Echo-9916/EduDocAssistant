package com.cidas.edudoc.coursework.application.impl;

import com.cidas.edudoc.document.application.DocumentProvisioningService;
import com.cidas.edudoc.document.application.DocumentProvisioningService.CreatedDocument;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.ObjectInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于作业附件建档写事务：OSS 复制在事务外完成，本组件只做「建档四写 + 绑定作业模板版本」同事务落库。
 */
@Component
@RequiredArgsConstructor
public class CourseworkAttachmentTxWriter {

    private final DocumentProvisioningService provisioningService;
    private final DocumentRepository documentRepository;

    @Transactional
    public CreatedDocument provision(UploadSession session, ObjectInfo info, String contentHash,
                                     String templateVersionId) {
        CreatedDocument created = provisioningService.provision(session, info, contentHash);
        if (templateVersionId != null) {
            documentRepository.updateTemplateVersion(created.documentId(), templateVersionId);
        }
        return created;
    }
}