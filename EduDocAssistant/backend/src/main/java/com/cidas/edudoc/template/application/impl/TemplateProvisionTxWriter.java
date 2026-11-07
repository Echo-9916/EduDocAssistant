package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.document.application.DocumentProvisioningService;
import com.cidas.edudoc.document.application.DocumentProvisioningService.CreatedDocument;
import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.ObjectInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按模板新建写事务（D-07，终审 I8）：OSS 生成/上传在事务外完成，本组件只做「建档四写 + 绑定模板版本」
 * 同事务原子落库——不绑定的半成品文档不会以未绑定态提交。
 */
@Component
@RequiredArgsConstructor
public class TemplateProvisionTxWriter {

    private final DocumentProvisioningService provisioningService;
    private final DocumentRepository documentRepository;

    @Transactional
    public CreatedDocument provisionAndBind(UploadSession session, ObjectInfo info, String contentHash,
                                            String templateVersionId) {
        CreatedDocument created = provisioningService.provision(session, info, contentHash);
        documentRepository.updateTemplateVersion(created.documentId(), templateVersionId);
        return created;
    }
}
