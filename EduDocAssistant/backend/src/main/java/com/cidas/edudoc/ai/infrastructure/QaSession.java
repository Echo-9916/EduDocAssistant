package com.cidas.edudoc.ai.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 问答会话持久化对象（edudoc_qa_session，status A 活跃 / C 归档）。 */
@Data
@AllArgsConstructor
public class QaSession {

    private String id;
    private String documentId;
    private String userId;
    private String title;
    private String status;
}
