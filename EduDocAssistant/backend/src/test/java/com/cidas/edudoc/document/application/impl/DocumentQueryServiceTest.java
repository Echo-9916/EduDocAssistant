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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final String VER_ID = "A1B2C3D4E5F60708090A0B0C0D0E0F11";

    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final com.cidas.edudoc.document.infrastructure.DocumentVersionRepository versionRepository =
            mock(com.cidas.edudoc.document.infrastructure.DocumentVersionRepository.class);
    private final com.cidas.edudoc.document.infrastructure.OssObjectRepository ossObjectRepository =
            mock(com.cidas.edudoc.document.infrastructure.OssObjectRepository.class);
    private final com.cidas.edudoc.infrastructure.oss.OssStorageAdapter ossStorageAdapter =
            mock(com.cidas.edudoc.infrastructure.oss.OssStorageAdapter.class);
    private final DocumentQueryServiceImpl service = new DocumentQueryServiceImpl(
            repository, versionRepository, ossObjectRepository, ossStorageAdapter);

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
        when(repository.page(eq("U1"), isNull(), isNull(), isNull(), isNull(), eq(0L), eq(20)))
                .thenReturn(List.of(row("READY")));

        PageResult<Summary> result = service.list(new ListQuery(null, null, null, null, null, null));

        assertEquals(1, result.total());
        assertEquals(DOC_ID, result.items().get(0).id());
        assertEquals(20, result.pageSize());
    }

    @Test
    void pageSizeClampedAndOffsetComputed() {
        when(repository.count(any(), any(), any(), any(), any())).thenReturn(30L);
        when(repository.page(any(), any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        service.list(new ListQuery(null, null, null, null, 3, 500));

        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(repository).page(eq("U1"), isNull(), isNull(), isNull(), isNull(),
                offset.capture(), limit.capture());
        assertEquals(200L, offset.getValue());
        assertEquals(100, limit.getValue());
    }

    @Test
    void hugePageDoesNotOverflowToFirstPage() {
        // 1103 审查 S3：int 溢出曾使 page=MAX_VALUE 静默返回首页数据
        when(repository.count(any(), any(), any(), any(), any())).thenReturn(30L);
        when(repository.page(any(), any(), any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());

        PageResult<Summary> result = service.list(new ListQuery(null, null, null, null, Integer.MAX_VALUE, 2));

        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        verify(repository).page(any(), any(), any(), any(), any(), offset.capture(), anyInt());
        assertEquals(4294967292L, offset.getValue());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void malformedCourseIdRejectedBeforeSql() {
        // 1103 审查 S2：courseId=y 曾穿透 HEXTORAW 触发 Oracle 异常 → 500/50001
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.list(new ListQuery("y", null, null, null, 1, 20)));
        assertEquals(ErrorCode.BAD_REQUEST, e.errorCode());
        verify(repository, never()).count(any(), any(), any(), any(), any());
    }

    @Test
    void keywordSpecialCharsEscaped() {
        when(repository.count(any(), any(), any(), any(), any())).thenReturn(0L);

        service.list(new ListQuery(null, null, null, "50%_x", 1, 20));

        verify(repository).count(eq("U1"), isNull(), isNull(), isNull(), eq("50\\%\\_x"));
        verify(repository, never()).page(any(), any(), any(), any(), any(), anyLong(), anyInt());
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

    // ==================== 验收决议④：版本历史 ====================

    private static com.cidas.edudoc.document.infrastructure.DocumentVersionRow verRow(
            String id, int no, String sourceObjectId) {
        var v = new com.cidas.edudoc.document.infrastructure.DocumentVersionRow();
        v.setVersionId(id);
        v.setVersionNo(no);
        v.setVersionType(no == 1 ? "UPLOAD" : "EDIT");
        v.setCreateTime("2026-11-09T10:00:0" + no);
        v.setChangeSummary(no == 1 ? "上传" : "一键修复");
        v.setSourceObjectId(sourceObjectId);
        return v;
    }

    @Test
    void listVersionsOwnerSeesDescendingRowsWithPresignedAndCurrentFlag() {
        DocumentViewRow doc = row("READY");
        doc.setCurrentVersionId("V2");
        when(repository.detail(DOC_ID)).thenReturn(Optional.of(doc));
        when(versionRepository.listByDocument(DOC_ID))
                .thenReturn(List.of(verRow("V2", 2, "SRC2"), verRow("V1", 1, null)));
        var obj = mock(com.cidas.edudoc.document.infrastructure.OssObject.class);
        when(obj.getObjectKey()).thenReturn("edudoc/documents/x/v2.docx");
        when(ossObjectRepository.findById("SRC2")).thenReturn(Optional.of(obj));
        when(ossStorageAdapter.presignGet("edudoc/documents/x/v2.docx", 900)).thenReturn("https://oss/signed-v2");

        var rows = service.listVersions(DOC_ID);

        assertEquals(2, rows.size());
        assertTrue(rows.get(0).current());
        assertEquals("https://oss/signed-v2", rows.get(0).downloadUrl());
        assertEquals("一键修复", rows.get(0).changeSummary());
        assertFalse(rows.get(1).current());
        assertNull(rows.get(1).downloadUrl()); // 无源对象则无下载链
    }

    @Test
    void listVersionsNonOwnerGets40401AndNoQuery() {
        DocumentViewRow other = row("READY");
        other.setOwnerId("OTHER");
        when(repository.detail(DOC_ID)).thenReturn(Optional.of(other));

        var e = assertThrows(CidasBusinessException.class, () -> service.listVersions(DOC_ID));
        assertEquals(40401, e.errorCode().code());
        verify(versionRepository, never()).listByDocument(any());
    }

    /** 格式视图 v3：版本源文件字节流（owner 校验同 detail；mime 随行）。 */
    @Test
    void versionContentStreamsSourceObjectForOwner() {
        DocumentViewRow doc = row("READY");
        doc.setMimeType("application/pdf");
        when(repository.detail(DOC_ID)).thenReturn(Optional.of(doc));
        var ver = new com.cidas.edudoc.document.infrastructure.DocumentVersion(
                VER_ID, DOC_ID, 1, "UPLOAD", "SRC1", "U1", "hash", "A");
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(ver));
        var obj = mock(com.cidas.edudoc.document.infrastructure.OssObject.class);
        when(obj.getObjectKey()).thenReturn("edudoc/uploads/a.pdf");
        when(ossObjectRepository.findById("SRC1")).thenReturn(Optional.of(obj));
        when(ossStorageAdapter.readBytes("edudoc/uploads/a.pdf", 20L * 1024 * 1024))
                .thenReturn(new byte[]{'%', 'P', 'D', 'F'});

        var content = service.versionContent(DOC_ID, VER_ID);
        assertEquals("application/pdf", content.contentType());
        assertEquals(4, content.content().length);
    }

    @Test
    void versionContentForeignDocumentInvisibleAs40401() {
        DocumentViewRow other = row("READY");
        other.setOwnerId("OTHER");
        when(repository.detail(DOC_ID)).thenReturn(Optional.of(other));

        var e = assertThrows(CidasBusinessException.class, () -> service.versionContent(DOC_ID, VER_ID));
        assertEquals(40401, e.errorCode().code());
    }
}
