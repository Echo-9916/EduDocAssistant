package com.cidas.edudoc.document.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.application.impl.DocumentPreviewServiceImpl;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentChunk;
import com.cidas.edudoc.document.infrastructure.DocumentChunkRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C-08 预览：owner 40401、非当前版本 40901、块序与 C-01 密铺偏移不变量。 */
class DocumentPreviewServiceTest {

    private static final String DOC_ID = "AA0000000000000000000000000000A1";
    private static final String VERSION = "AA0000000000000000000000000000B1";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final DocumentPreviewService service =
            new DocumentPreviewServiceImpl(documentRepository, versionRepository, chunkRepository);

    @BeforeEach
    void login() {
        AuthContext.set(new UserPrincipal("U1", "stu", null, Set.of(), Set.of("DOCUMENT_READ"), Set.of()));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "标题", "PDF", "READY", VERSION)));
        when(versionRepository.findById(VERSION)).thenReturn(Optional.of(
                new DocumentVersion(VERSION, DOC_ID, 1, "UPLOAD", "O1", "U1", "h", "A")));
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    @Test
    void returnsBlocksSortedWithTilingInvariants() {
        // C-01 口径：闭区间、块文本=全文切片、相邻 char_end+1=后块 char_start（连接符归后块）
        when(chunkRepository.selectAllByVersion(VERSION)).thenReturn(List.of(
                new DocumentChunk("C1", VERSION, 1, null, 1, 0, 4, "hello", 4, "h", "READY"),
                new DocumentChunk("C2", VERSION, 2, "H1", 1, 5, 10, "\nworld", 4, "h", "READY"),
                new DocumentChunk("C3", VERSION, 3, "H1", 2, 11, 16, "\nagain", 4, "h", "READY")));

        DocumentPreviewService.Preview preview = service.preview(DOC_ID, null);

        assertEquals(1, preview.versionNo());
        assertEquals(3, preview.blocks().size());
        assertEquals(0, preview.blocks().get(0).charStart(), "首块 char_start=0");
        String full = "hello\nworld\nagain";
        int prevEnd = -1;
        for (var block : preview.blocks()) {
            assertEquals(prevEnd + 1, block.charStart(), "块间连续");
            assertEquals(full.substring(block.charStart(), block.charEnd() + 1), block.text());
            prevEnd = block.charEnd();
        }
        assertEquals(full.length() - 1, prevEnd);
        assertTrue(preview.vectorReady(), "全 READY 块 → vectorReady=true");
    }

    @Test
    void pendingChunksMakeVectorReadyFalse() {
        // 1106 P2-1/R3：文本 READY 但索引未完成时必须暴露 vectorReady=false
        when(chunkRepository.selectAllByVersion(VERSION)).thenReturn(List.of(
                new DocumentChunk("C1", VERSION, 1, null, 1, 0, 4, "hello", 4, "h", "READY"),
                new DocumentChunk("C2", VERSION, 2, "H1", 1, 5, 10, "\nworld", 4, "h", "PENDING")));

        assertFalse(service.preview(DOC_ID, null).vectorReady());
    }

    @Test
    void nonCurrentVersionRejectedWith40901() {
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.preview(DOC_ID, "CC0000000000000000000000000000X9"));
        assertEquals(ErrorCode.CONFLICT, e.errorCode());
    }

    @Test
    void foreignDocumentIs40401() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "OTHER", "标题", "PDF", "READY", VERSION)));
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.preview(DOC_ID, null));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }

    @Test
    void malformedDocumentIdIs40401() {
        CidasBusinessException e = assertThrows(CidasBusinessException.class,
                () -> service.preview("not-hex", null));
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode());
    }
}
