package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.DocumentQueryService.Detail;
import com.cidas.edudoc.document.application.DocumentQueryService.ListQuery;
import com.cidas.edudoc.document.application.DocumentQueryService.Summary;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentViewRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B-07：owner 强制、默认排除回收站、分页钳制、LIKE 转义、非 owner 40401。 */
class DocumentQueryServiceTest {

    private static final String DOC_ID = "D1EAD1EAD1EAD1EAD1EAD1EAD1EAD1EA";

    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final DocumentQueryServiceImpl service = new DocumentQueryServiceImpl(repository);

    @BeforeEach
    void setUp() {
        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of()));
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private static DocumentViewRow row(String status) {
        DocumentViewRow row = new DocumentViewRow();
        row.setId(DOC_ID);
        row.setOwnerId("U1");
        row.setTitle("t");
        row.setStatus(status);
        row.setDocType("COURSEWARE");
        return row;
    }

    @Test
    void listIsScopedToOwnerWithDefaults() {
        when(repository.count(eq("U1"), isNull(), isNull(), isNull(), isNull())).thenReturn(1L);
        when(repository.page(eq("U1"), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(row("READY")));

        PageResult<Summary> result = service.list(new ListQuery(null, null, null, null, null, null));

        assertEquals(1, result.total());
        assertEquals(DOC_ID, result.items().get(0).id());
        assertEquals(20, result.pageSize());
    }

    @Test
    void pageSizeClampedAndOffsetComputed() {
        when(repository.count(any(), any(), any(), any(), any())).thenReturn(30L);
        when(repository.page(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.list(new ListQuery(null, null, null, null, 3, 500));

        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(repository).page(eq("U1"), isNull(), isNull(), isNull(), isNull(),
                offset.capture(), limit.capture());
        assertEquals(200, offset.getValue());
        assertEquals(100, limit.getValue());
    }

    @Test
    void keywordSpecialCharsEscaped() {
        when(repository.count(any(), any(), any(), any(), any())).thenReturn(0L);

        service.list(new ListQuery(null, null, null, "50%_x", 1, 20));

        verify(repository).count(eq("U1"), isNull(), isNull(), isNull(), eq("50\\%\\_x"));
        verify(repository, never()).page(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void explicitTrashStatusPassesThrough() {
        when(repository.count(any(), any(), any(), any(), any())).thenReturn(0L);

        service.list(new ListQuery(null, null, "DELETED", null, 1, 20));

        verify(repository).count(eq("U1"), isNull(), isNull(), eq("DELETED"), isNull());
    }

    @Test
    void detailMalformedIdIsBadRequest() {
        CidasBusinessException e = assertThrows(CidasBusinessException.class, () -> service.detail("x"));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
    }

    @Test
    void detailOfOtherUsersDocumentIsNotFound() {
        DocumentViewRow other = row("READY");
        other.setOwnerId("OTHER");
        when(repository.detail(DOC_ID)).thenReturn(Optional.of(other));

        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.detail(DOC_ID));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }

    @Test
    void detailCarriesVersionAndTrashFields() {
        DocumentViewRow row = row("DELETED");
        row.setChunkCount(3);
        row.setPurgeAt("2026-12-03T09:00:00");
        when(repository.detail(DOC_ID)).thenReturn(Optional.of(row));

        Detail detail = service.detail(DOC_ID);

        assertEquals(3, detail.chunkCount());
        assertEquals("2026-12-03T09:00:00", detail.purgeAt());
        assertNull(detail.deletedAt());
        assertTrue(detail.status().equals("DELETED"));
    }
}
