package com.cidas.edudoc.ai.application;

import com.cidas.edudoc.ai.infrastructure.AiCitation;
import com.cidas.edudoc.ai.infrastructure.AiResult;

import java.util.List;

/** 结果写回事务端口（C-04）：守卫式终态迁移 + 结果与引用同事务落库。 */
public interface AiResultTxService {

    /** 仅当任务仍为 RUNNING 时写入；false 表示已取消/已终态，本事务不留下任何结果行。 */
    boolean completeTask(AiResult result, List<AiCitation> citations);
}
