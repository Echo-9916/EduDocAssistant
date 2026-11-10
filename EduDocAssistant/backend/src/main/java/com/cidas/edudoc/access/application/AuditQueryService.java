package com.cidas.edudoc.access.application;

import com.cidas.edudoc.common.api.PageResult;

import java.util.List;

/** 审计事件检索与详情聚合（06 切片 1 · REQ-09，只读；写入路径仍归 {@link AuditService}）。 */
public interface AuditQueryService {

    /** 运维端「操作日志」列表：时间窗 + 对象类型 + 操作人 + 关键字，SQL 分页。 */
    PageResult<AuditEventView> search(ListQuery query);

    /** 运维端「事件详情」：主行 + 同 traceId 链路 + 影响条目 + 处置建议。 */
    AuditEventDetailView detail(String eventId);

    record ListQuery(String range, String resourceType, String actor, String keyword,
                     Integer page, Integer pageSize) {
    }

    /** 列表行：result 由 resultCode 派生（'200'/'SUCCESS'→SUCCESS，否则 FAIL），riskLevel 现算。 */
    record AuditEventView(String id, String occurredAt, String actor, String actorAccount,
                          String actionType, String resourceType, String resourceRef,
                          String result, String resultCode, String traceId, String sourceIp,
                          String userAgent, String riskLevel, String summary) {
    }

    /** 详情：主行字段 + chain（同 trace_id 全部审计行，occurred_at 升序）+ impacts + advice。 */
    record AuditEventDetailView(String id, String occurredAt, String actor, String actorAccount,
                                String actionType, String resourceType, String resourceRef,
                                String result, String resultCode, String traceId, String sourceIp,
                                String userAgent, String riskLevel, String summary,
                                List<ChainStep> chain, List<Impact> impacts, List<String> advice) {
    }

    record ChainStep(int step, String at, String detail) {
    }

    record Impact(String type, String name, String detail) {
    }
}
