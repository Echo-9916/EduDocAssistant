package com.cidas.edudoc.document.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** OSS 对象登记持久化对象（edudoc_oss_object，id 等 RAW(16) 转 hex 字符串）。 */
@Data
@AllArgsConstructor
public class OssObject {

    private String id;
    private String bucketName;
    private String region;
    private String objectKey;
    private String objectVersionId;
    private String etag;
    private String contentHash;
    private long sizeBytes;
    private String mimeType;
    private String encryptionType;
    private String objectRole;
}
