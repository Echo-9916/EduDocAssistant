package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.api.AuditQueryController;
import com.cidas.edudoc.access.application.AuditQueryService.AuditEventDetailView;
import com.cidas.edudoc.access.application.AuditQueryService.AuditEventView;
import com.cidas.edudoc.access.application.AuditQueryService.ChainStep;
import com.cidas.edudoc.access.application.AuditQueryService.ListQuery;
import com.cidas.edudoc.access.application.impl.AuditQueryServiceImpl;
import com.cidas.edudoc.access.domain.PermissionCode;
import com.cidas.edudoc.access.infrastructure.AuditLogMapper;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.AccessGuard;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.RequirePermission;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 审计事件检索与详情聚合单测（纯 Mockito，不起 Spring 容器）。 */
class AuditQueryServiceTest {

    private static final String EVENT_ID = "0123456789ABCDEF0123456789ABCDEF";

    private final AuditLogMapper mapper = mock(AuditLogMapper.class);
    private final AuditQueryServiceImpl service = new AuditQueryServiceImpl(mapper);

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> eventRow(String id, String occurredAt, String resultCode, int failCount) {
        return row("id", id, "occurredAt", occurredAt, "actor", "陈同学", "actorAccount", "2024017",
                "actionType", "DOC_PREVIEW", "resourceType", "DOCUMENT", "resourceRef", "D1",
                "resultCode", resultCode, "traceId", "tr-1", "sourceIp", "10.20.12.5",
                "userAgent", "Chrome 130", "failCount", failCount);
    }

    /** 40301 由 @RequirePermission→AccessGuard 既有链路负责：验证两个端点注解挂对，且缺权限时链路拒绝。 */
    @Test
    void missingAuditReadIsDeniedByExistingGuardChain() throws Exception {
        RequirePermission listAnn = AuditQueryController.class
                .getMethod("list", String.class, String.class, String.class, String.class, Integer.class, Integer.class)
                .getAnnotation(RequirePermission.class);
        RequirePermission detailAnn = AuditQueryController.class
                .getMethod("detail", String.class)
                .getAnnotation(RequirePermission.class);
        assertNotNull(listAnn);
        assertNotNull(detailAnn);
        assertEquals(PermissionCode.AUDIT_READ, listAnn.value());
        assertEquals(PermissionCode.AUDIT_READ, detailAnn.value());

        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"), Set.of("DOCUMENT_READ"), Set.of()));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> new AccessGuard().guard(mock(ProceedingJoinPoint.class), listAnn));
        assertEquals(ErrorCode.FORBIDDEN, e.errorCode());
    }

    @Test
    void invalidRangeOrResourceTypeIsRejected() {
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.search(new ListQuery("90d", null, null, null, null, null))).errorCode());
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.search(new ListQuery("24h", "FILE", null, null, null, null))).errorCode());
        verifyNoInteractions(mapper);
    }

    /** range 缺省 24h：窗口下界恰为 now-24h；SQL 下界为 >=（AuditLogMapper.xml searchWhere），整点那条不吞。 */
    @Test
    void defaultRangeWindowLowerBoundIsExactly24hAgo() {
        when(mapper.countEvents(any(), any(), any(), any())).thenReturn(0L);
        OffsetDateTime earliest = OffsetDateTime.now().minusHours(24);
        service.search(new ListQuery(null, null, null, null, null, null));
        OffsetDateTime latest = OffsetDateTime.now().minusHours(24);

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).countEvents(captor.capture(), isNull(), isNull(), isNull());
        OffsetDateTime since = captor.getValue();
        assertFalse(since.isBefore(earliest));
        assertFalse(since.isAfter(latest));
    }

    @Test
    void emptyResultReturnsEmptyItemsWithoutPageQuery() {
        when(mapper.countEvents(any(), any(), any(), any())).thenReturn(0L);

        PageResult<AuditEventView> result = service.search(new ListQuery("7d", null, null, null, null, null));

        assertEquals(0L, result.total());
        assertEquals(List.of(), result.items());
        verify(mapper, never()).searchEvents(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void derivesResultRiskLevelAndSummary() {
        when(mapper.countEvents(any(), any(), any(), any())).thenReturn(3L);
        when(mapper.searchEvents(any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of(
                eventRow("A1", "2026-11-09T10:42:18", "40302", 2),
                eventRow("A2", "2026-11-09T09:00:00", "50001", 1),
                eventRow("A3", "2026-11-09T08:00:00", "200", 0)));

        List<AuditEventView> items = service.search(new ListQuery("24h", null, null, null, 1, 20)).items();

        assertEquals("FAIL", items.get(0).result());
        assertEquals("HIGH", items.get(0).riskLevel());
        assertTrue(items.get(0).summary().contains("陈同学"));
        assertEquals("FAIL", items.get(1).result());
        assertEquals("MEDIUM", items.get(1).riskLevel());
        assertEquals("SUCCESS", items.get(2).result());
        assertEquals("LOW", items.get(2).riskLevel());
    }

    @Test
    void detailRejectsBadIdAndMissingRow() {
        assertEquals(ErrorCode.BAD_REQUEST, assertThrows(CidasBusinessException.class,
                () -> service.detail("xyz")).errorCode());
        when(mapper.selectEventDetail(EVENT_ID)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(CidasBusinessException.class,
                () -> service.detail(EVENT_ID)).errorCode());
    }

    /** 同 traceId 多行聚合成一条 chain：DB 返回顺序故意打乱，step 仍按 occurred_at 升序编号。 */
    @Test
    void detailAggregatesChainInOccurredAtOrder() {
        Map<String, Object> main = eventRow(EVENT_ID, "2026-11-09T10:42:18", "40302", 1);
        main.put("documentId", null);
        main.put("documentTitle", null);
        when(mapper.selectEventDetail(EVENT_ID)).thenReturn(main);
        when(mapper.selectChainByTraceId("tr-1")).thenReturn(List.of(
                row("id", "C3", "occurredAt", "2026-11-09T10:42:20", "actionType", "AUDIT_WRITTEN",
                        "resourceType", "SYSTEM", "resourceRef", "tr-1", "resultCode", "200"),
                row("id", "C1", "occurredAt", "2026-11-09T10:42:17", "actionType", "AUTH_CHECK",
                        "resourceType", "ACCOUNT", "resourceRef", "2024017", "resultCode", "200"),
                row("id", "C2", "occurredAt", "2026-11-09T10:42:18", "actionType", "ACL_DENIED",
                        "resourceType", "DOCUMENT", "resourceRef", "D1", "resultCode", "40302")));

        AuditEventDetailView detail = service.detail(EVENT_ID);

        assertEquals(List.of(1, 2, 3), detail.chain().stream().map(ChainStep::step).toList());
        assertEquals(List.of("2026-11-09T10:42:17", "2026-11-09T10:42:18", "2026-11-09T10:42:20"),
                detail.chain().stream().map(ChainStep::at).toList());
        assertEquals("MEDIUM", detail.riskLevel());
        assertEquals(1, detail.impacts().size());
        assertEquals("文档", detail.impacts().get(0).type());
        assertFalse(detail.advice().isEmpty());
    }
}
