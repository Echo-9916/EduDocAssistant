package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.AuditQueryService;
import com.cidas.edudoc.access.infrastructure.AuditLogMapper;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 审计事件检索与详情聚合（06 切片 1，只读）：SQL 全部在 AuditLogMapper.xml，这里只做校验与装配。 */
@Service
@RequiredArgsConstructor
public class AuditQueryServiceImpl implements AuditQueryService {

    private static final String DEFAULT_RANGE = "24h";
    private static final Map<String, Integer> RANGE_HOURS = Map.of("24h", 24, "7d", 7 * 24, "30d", 30 * 24);
    private static final Set<String> RESOURCE_TYPES = Set.of("ACCOUNT", "COURSE", "DOCUMENT", "PERMISSION", "SYSTEM");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // ponytail: 固定阈值，真做风险评分时改走 edudoc_anomaly_review（切片4）
    private static final int RISK_HIGH_FAILS = 2;
    private static final int RISK_MEDIUM_FAILS = 1;

    private final AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditEventView> search(ListQuery query) {
        String range = query.range() == null || query.range().isBlank() ? DEFAULT_RANGE : query.range().trim();
        Integer hours = RANGE_HOURS.get(range);
        if (hours == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "range 仅支持 24h、7d、30d");
        }
        String resourceType = blankToNull(query.resourceType());
        if (resourceType != null && !RESOURCE_TYPES.contains(resourceType)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                    "resourceType 仅支持 ACCOUNT、COURSE、DOCUMENT、PERMISSION、SYSTEM");
        }
        // 库列为 TIMESTAMP WITH TIME ZONE，窗口一律 OffsetDateTime；下界为闭区间（SQL 用 >=），整点那条不吞
        OffsetDateTime since = OffsetDateTime.now().minusHours(hours);
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int pageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        long offset = (long) (page - 1) * pageSize;
        String actor = escapeLike(query.actor());
        String keyword = escapeLike(query.keyword());
        long total = auditLogMapper.countEvents(since, resourceType, actor, keyword);
        List<AuditEventView> items = total == 0 ? List.of()
                : auditLogMapper.searchEvents(since, resourceType, actor, keyword, offset, pageSize)
                        .stream().map(AuditQueryServiceImpl::toView).toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    public AuditEventDetailView detail(String eventId) {
        if (!Ids.isHex16(eventId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "事件 ID 格式不合法");
        }
        Map<String, Object> row = auditLogMapper.selectEventDetail(eventId);
        if (row == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "审计事件不存在");
        }
        AuditEventView base = toView(row);
        return new AuditEventDetailView(base.id(), base.occurredAt(), base.actor(), base.actorAccount(),
                base.actionType(), base.resourceType(), base.resourceRef(), base.result(), base.resultCode(),
                base.traceId(), base.sourceIp(), base.userAgent(), base.riskLevel(), base.summary(),
                chainOf(base.traceId()), impactsOf(row, base.result()), adviceFor(base.resultCode()));
    }

    /** 同 trace_id 的全部审计行聚合成链路；Java 侧再按 occurred_at+id 排序，step 编号不依赖 DB 返回顺序。 */
    private List<ChainStep> chainOf(String traceId) {
        List<Map<String, Object>> rows = auditLogMapper.selectChainByTraceId(traceId).stream()
                .sorted(Comparator.comparing((Map<String, Object> r) -> str(r, "occurredAt"))
                        .thenComparing(r -> str(r, "id")))
                .toList();
        List<ChainStep> chain = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            chain.add(new ChainStep(i + 1, str(r, "occurredAt"),
                    str(r, "actionType") + " → " + label(str(r, "resourceType")) + " " + str(r, "resourceRef")
                            + "，结果码 " + str(r, "resultCode")));
        }
        return chain;
    }

    /** 影响条目：本行 resource_type/resource_ref 一条，document_id 存在且与 ref 不同再拆一条。 */
    private static List<Impact> impactsOf(Map<String, Object> row, String result) {
        String resourceRef = str(row, "resourceRef");
        List<Impact> impacts = new ArrayList<>(2);
        impacts.add(new Impact(label(str(row, "resourceType")), resourceRef,
                "动作 " + str(row, "actionType") + "，结果" + resultText(result)
                        + "（" + str(row, "resultCode") + "）"));
        String documentId = str(row, "documentId");
        if (documentId != null && !documentId.equalsIgnoreCase(resourceRef)) {
            String title = str(row, "documentTitle");
            impacts.add(new Impact("文档", title != null ? title : documentId, "关联文档 ID " + documentId));
        }
        return impacts;
    }

    /** 处置建议：按结果码给固定中文文案（成功 / 越权 / 其他失败），不做规则引擎。 */
    private static List<String> adviceFor(String resultCode) {
        if (isSuccess(resultCode)) {
            return List.of("操作成功，无需处置。", "保留审计记录，满足例行检查与追踪要求。");
        }
        if (resultCode != null && resultCode.startsWith("403")) {
            return List.of("核实操作人权限与资源归属是否匹配。",
                    "若 24 小时内再次出现同类越权，升级为高风险安全事件复核。",
                    "保留全部请求日志，满足安全调查与审计追踪要求。");
        }
        return List.of("核实操作人账号状态与权限是否正常。",
                "短时间内重复出现同类失败时，转人工复核。",
                "保留相关日志与 traceId，便于问题定位。");
    }

    private static AuditEventView toView(Map<String, Object> row) {
        String resultCode = str(row, "resultCode");
        String result = isSuccess(resultCode) ? "SUCCESS" : "FAIL";
        return new AuditEventView(str(row, "id"), str(row, "occurredAt"), str(row, "actor"),
                str(row, "actorAccount"), str(row, "actionType"), str(row, "resourceType"),
                str(row, "resourceRef"), result, resultCode, str(row, "traceId"),
                str(row, "sourceIp"), str(row, "userAgent"), riskLevelOf(row), summaryOf(row, result));
    }

    /** 成功码口径：通用链路写 '200'，recordAuth 成功路径写字面量 'SUCCESS'，两者都算成功。 */
    private static boolean isSuccess(String resultCode) {
        return "200".equals(resultCode) || "SUCCESS".equals(resultCode);
    }

    /** 风险等级现算：同一操作人窗口内 FAIL 条数 ≥2 HIGH、1 MEDIUM、否则 LOW（SQL 已带 failCount）。 */
    private static String riskLevelOf(Map<String, Object> row) {
        Object failCount = row.get("failCount");
        int fails = failCount instanceof Number n ? n.intValue() : 0;
        if (fails >= RISK_HIGH_FAILS) {
            return "HIGH";
        }
        return fails >= RISK_MEDIUM_FAILS ? "MEDIUM" : "LOW";
    }

    private static String summaryOf(Map<String, Object> row, String result) {
        String actor = str(row, "actor");
        String who = actor != null ? actor
                : str(row, "actorAccount") != null ? str(row, "actorAccount") : "系统";
        return who + " 对" + label(str(row, "resourceType")) + " " + str(row, "resourceRef")
                + " 执行 " + str(row, "actionType") + "，" + resultText(result)
                + "（" + str(row, "resultCode") + "）";
    }

    private static String resultText(String result) {
        return "SUCCESS".equals(result) ? "成功" : "失败";
    }

    private static String label(String resourceType) {
        if (resourceType == null) {
            return "对象";
        }
        return switch (resourceType) {
            case "ACCOUNT" -> "账号";
            case "COURSE" -> "课程";
            case "DOCUMENT" -> "文档";
            case "PERMISSION" -> "权限";
            case "SYSTEM" -> "系统";
            default -> resourceType;
        };
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** LIKE 元字符转义（ESCAPE '\'，同 DocumentQueryServiceImpl 口径）；未提供返回 null。 */
    private static String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
