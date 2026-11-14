package com.cidas.edudoc.document.application;

import com.cidas.edudoc.common.api.PageResult;

/** 本人文档列表与详情查询（详细设计 §3.1.3 GET / 与 GET /{id}）。 */
public interface DocumentQueryService {

    PageResult<Summary> list(ListQuery query);

    Detail detail(String documentId);

    /** 验收决议④：文档版本历史（owner 可见，版本号降序，源对象带短期签名下载链）。 */
    java.util.List<VersionView> listVersions(String documentId);

    /** 格式视图字节流：某版本源文件（owner 校验同 detail 口径；20MB 护栏）。 */
    VersionContent versionContent(String documentId, String versionId);

    record VersionContent(byte[] content, String contentType) {
    }

    record ListQuery(String courseId, String docType, String status, String keyword,
                     Integer page, Integer pageSize) {
    }

    record Summary(String id, String title, String docType, String status, String courseId,
                   String courseName, Long sizeBytes, String mimeType, Integer versionNo,
                   String createTime, String updateTime, String ownerName, String relation) {
    }

    record VersionView(String versionId, int versionNo, String versionType, String createTime,
                      String changeSummary, String downloadUrl, String previewUrl, boolean current) {
    }

    record Detail(String id, String title, String docType, String status, String courseId,
                  String courseName, Long sizeBytes, String mimeType, Integer versionNo,
                  String currentVersionId, String contentHash, Integer chunkCount,
                  String createTime, String updateTime, String deletedAt, String purgeAt) {
    }
}
