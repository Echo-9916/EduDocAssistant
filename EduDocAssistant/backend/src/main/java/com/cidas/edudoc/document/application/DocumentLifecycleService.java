package com.cidas.edudoc.document.application;

/** 文档生命周期：软删回收站、恢复、重新解析（详细设计 §3.1.3 后三端点）。 */
public interface DocumentLifecycleService {

    void delete(String documentId);

    void restore(String documentId);

    /** 发布重解析事件，返回 taskId（= outbox eventId hex）。 */
    String reparse(String documentId, String versionId);
}
