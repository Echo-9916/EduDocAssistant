package com.cidas.edudoc.document.application;

import com.cidas.edudoc.common.api.PageResult;

/** 本人文档列表与详情查询（详细设计 §3.1.3 GET / 与 GET /{id}）。 */
public interface DocumentQueryService {

    PageResult<Summary> list(ListQuery query);

    Detail detail(String documentId);

    record ListQuery(String courseId, String docType, String status, String keyword,
                     Integer page, Integer pageSize) {
    }

    record Summary(String id, String title, String docType, String status, String courseId,
                   String courseName, Long sizeBytes, String mimeType, Integer versionNo,
                   String createTime, String updateTime) {
    }

    record Detail(String id, String title, String docType, String status, String courseId,
                  String courseName, Long sizeBytes, String mimeType, Integer versionNo,
                  String currentVersionId, String contentHash, Integer chunkCount,
                  String createTime, String updateTime, String deletedAt, String purgeAt) {
    }
}
