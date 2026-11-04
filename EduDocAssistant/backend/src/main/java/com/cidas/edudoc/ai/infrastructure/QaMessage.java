package com.cidas.edudoc.ai.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 问答消息持久化对象（edudoc_qa_message，READY/FAILED/REFUSED）。 */
@Data
@AllArgsConstructor
public class QaMessage {

    private String id;
    private String sessionId;
    private String role;
    private String contentText;
    private String aiTaskId;
    private int sequenceNo;
    private String status;
}
