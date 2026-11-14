package com.cidas.edudoc.document.application;

/** 文档生命周期：软删回收站、恢复、重新解析（详细设计 §3.1.3 后三端点）。 */
public interface DocumentLifecycleService {

    void delete(String documentId);

    void restore(String documentId);

    /** 发布重解析事件，返回 taskId（= outbox eventId hex）。 */
    String reparse(String documentId, String versionId);

    /**
     * 恢复历史版本：复制该版本为新版本（version_type=RESTORE，同一源对象与解析文本），
     * 前移当前版本指针；历史版本只读不改。
     */
    RestoredVersion restoreVersion(String documentId, String versionId);

    record RestoredVersion(String versionId, int versionNo) {
    }
}
