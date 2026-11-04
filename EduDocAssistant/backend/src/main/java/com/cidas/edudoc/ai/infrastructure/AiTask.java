package com.cidas.edudoc.ai.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** AI 任务持久化对象（edudoc_ai_task，状态机 QUEUED→RUNNING→SUCCEEDED/FAILED/CANCELED）。 */
@Data
@AllArgsConstructor
public class AiTask {

    private String id;
    private String documentId;
    private String documentVersionId;
    private String taskType;
    private String status;
    private int priority;
    private String modelProfileId;
    private String requestPayload;
    private String requestedBy;
    private int progress;
    private String idempotencyKey;
    private String errorCode;
    private int retryCount;
}
