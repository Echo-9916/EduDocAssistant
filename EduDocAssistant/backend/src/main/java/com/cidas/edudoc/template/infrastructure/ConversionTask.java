package com.cidas.edudoc.template.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 格式转换任务持久化对象（edudoc_conversion_task）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversionTask {

    private String id;
    private String documentId;
    private String sourceVersionId;
    private String sourceObjectId;
    private String targetFormat;
    private String status;
    private int progress;
    private String resultObjectId;
    private String failureCode;
    private String failureMessage;
    private String requestedBy;
    private int retryCount;
    private String startedAt;
    private String finishedAt;
}
