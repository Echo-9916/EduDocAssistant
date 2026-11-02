package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.UploadSession;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.ObjectInfo;

import java.util.Optional;

/** 上传建档：单事务写入 oss_object/document/version/outbox/审计（详细设计 §3.1.2 伪代码）。 */
public interface DocumentProvisioningService {

    CreatedDocument provision(UploadSession session, ObjectInfo objectInfo, String contentHash);

    /** 恢复路径：事务已提交但 Redis 回填失败时，按 objectKey 反查已建档结果。 */
    Optional<CreatedDocument> findByObjectKey(String objectKey);

    record CreatedDocument(String documentId, String versionId) {
    }
}
