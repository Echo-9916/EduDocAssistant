package com.cidas.edudoc.document.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 文档列表/详情查询行（join 当前版本、OSS 对象与课程；时间已在 SQL 侧 TO_CHAR）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentViewRow {

    private String id;
    private String ownerId;
    private String title;
    private String docType;
    private String status;
    private String courseId;
    private String courseName;
    private Long sizeBytes;
    private String mimeType;
    private Integer versionNo;
    private String versionId;
    private String currentVersionId;
    private String contentHash;
    private Integer chunkCount;
    private String createTime;
    private String updateTime;
    private String deletedAt;
    private String purgeAt;
}
