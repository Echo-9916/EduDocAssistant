package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.application.impl.OpsUserServiceImpl;
import com.cidas.edudoc.access.infrastructure.OpsUserMapper;
import com.cidas.edudoc.access.infrastructure.OpsUserRow;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 运维用户列表与冻结/解冻：自冻结/OPS 封禁、状态条件迁移、审计同事务。 */
class OpsUserServiceTest {

    private static final String OPS_ID = "AA0000000000000000000000000000A1";
    private static final String USER_ID = "BB0000000000000000000000000000B1";

    private final OpsUserMapper mapper = mock(OpsUserMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final OpsUserService service = new OpsUserServiceImpl(mapper, auditService);

    @BeforeEach
    void setUp() {
        AuthContext.set(new UserPrincipal(OPS_ID, "ops", "赵工", Set.of("OPS"),
                Set.of("ROLE_MANAGE"), Set.of()));
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private static OpsUserRow row(String status) {
        OpsUserRow row = new OpsUserRow();
        row.setId(USER_ID);
        row.setUsername("student");
        row.setDisplayName("张同学");
        row.setRole("STUDENT");
        row.setAccountStatus(status);
        return row;
    }

    @Test
    void listMapsRowsAndClampsPage() {
        when(mapper.count(null, null, null)).thenReturn(1L);
        when(mapper.selectPage(null, null, null, 0L, 20)).thenReturn(List.of(row("A")));

        var result = service.list(new OpsUserService.Query(null, null, null, 1, 20));

        assertEquals(1, result.total());
        assertEquals("张同学", result.items().get(0).displayName());
        assertEquals("STUDENT", result.items().get(0).role());
    }

    @Test
    void freezeWritesStatusAndAudit() {
        when(mapper.selectById(USER_ID)).thenReturn(row("A"));
        when(mapper.countRole(USER_ID, "OPS")).thenReturn(0);
        when(mapper.updateStatus(USER_ID, "A", "L")).thenReturn(1);

        service.freeze(USER_ID);

        verify(mapper).updateStatus(USER_ID, "A", "L");
        verify(auditService).record(eq("ACCOUNT_FROZEN"), eq("ACCOUNT"), eq("student"), eq(null),
                eq("200"), any());
    }

    @Test
    void cannotFreezeSelf() {
        assertCode(ErrorCode.CONFLICT, () -> service.freeze(OPS_ID));
        verify(mapper, never()).updateStatus(anyString(), anyString(), anyString());
    }

    @Test
    void cannotFreezeOpsAccount() {
        when(mapper.selectById(USER_ID)).thenReturn(row("A"));
        when(mapper.countRole(USER_ID, "OPS")).thenReturn(1);

        assertCode(ErrorCode.CONFLICT, () -> service.freeze(USER_ID));
        verify(mapper, never()).updateStatus(anyString(), anyString(), anyString());
    }

    @Test
    void freezeRequiresActiveStatus() {
        when(mapper.selectById(USER_ID)).thenReturn(row("L"));

        assertCode(ErrorCode.CONFLICT, () -> service.freeze(USER_ID));
    }

    @Test
    void unfreezeRestoresActiveAndAudits() {
        when(mapper.selectById(USER_ID)).thenReturn(row("L"));
        when(mapper.countRole(USER_ID, "OPS")).thenReturn(0);
        when(mapper.updateStatus(USER_ID, "L", "A")).thenReturn(1);

        service.unfreeze(USER_ID);

        verify(auditService).record(eq("ACCOUNT_UNFROZEN"), eq("ACCOUNT"), eq("student"), eq(null),
                eq("200"), any());
    }

    private static void assertCode(ErrorCode expected, Runnable action) {
        CidasBusinessException e = assertThrows(CidasBusinessException.class, action::run);
        assertEquals(expected, e.errorCode());
    }
}