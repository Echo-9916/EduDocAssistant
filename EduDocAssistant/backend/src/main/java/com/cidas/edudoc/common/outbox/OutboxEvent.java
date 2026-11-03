package com.cidas.edudoc.common.outbox;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Outbox 事件持久化对象（edudoc_outbox_event，与业务事务同库提交；概要 §2.2）。 */
@Data
@AllArgsConstructor
public class OutboxEvent {

    private String id;
    private String aggregateType;
    private String aggregateRef;
    private String eventType;
    private String payloadJson;
    private int retryCount;
    private String status;
}
