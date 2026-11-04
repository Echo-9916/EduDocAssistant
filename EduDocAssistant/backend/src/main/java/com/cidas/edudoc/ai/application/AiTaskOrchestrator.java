package com.cidas.edudoc.ai.application;

import java.util.List;

/**
 * AI 任务编排端口（C-04）：创建（就绪校验 + 权限 + owner 口径 + 活跃去重）、查询（非本人 40401）、取消。
 */
public interface AiTaskOrchestrator {

    /** SUMMARY：POST /documents/{id}/summaries；KEYPOINT：POST /documents/{id}/keypoints。 */
    String createTask(String documentId, String taskType);

    TaskView getTask(String taskId);

    /** QUEUED→CANCELED 或 RUNNING 置取消（结果写回守卫兜住竞争）；终态 40901；非本人 40401。 */
    void cancel(String taskId);

    record CitationView(String citationId, String chunkId, Integer pageNo,
                        Integer charStart, Integer charEnd, String quoteText, int rankNo) {
    }

    record ResultView(String resultId, String resultType, String contentText, String structuredJson,
                      String modelName, String promptVersion, Integer inputTokens, Integer outputTokens,
                      List<CitationView> citations) {
    }

    record TaskView(String taskId, String documentId, String taskType, String status,
                    int progress, String errorCode, ResultView result) {
    }
}
