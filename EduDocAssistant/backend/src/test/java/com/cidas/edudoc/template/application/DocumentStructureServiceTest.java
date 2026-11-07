package com.cidas.edudoc.template.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.DocumentStructureService.ElementView;
import com.cidas.edudoc.template.application.impl.DocumentStructureServiceImpl;
import com.cidas.edudoc.template.domain.ElementDraft;
import com.cidas.edudoc.template.infrastructure.DocxStructureParser;
import com.cidas.edudoc.template.infrastructure.DocumentElement;
import com.cidas.edudoc.template.infrastructure.DocumentElementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-03 结构树服务：懒解析入库、缓存命中不重下载、非 owner 40401、非 docx 空清单。 */
class DocumentStructureServiceTest {

    private static final String DOC_ID = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String VER_ID = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String OBJ_ID = "C1B2C3D4E5F60708090A0B0C0D0E0F10";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);
    private final OssObjectRepository ossObjectRepository = mock(OssObjectRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final DocxStructureParser parser = mock(DocxStructureParser.class);
    private final DocumentElementRepository elementRepository = mock(DocumentElementRepository.class);
    private final DocumentStructureService service = new DocumentStructureServiceImpl(
            documentRepository, versionRepository, ossObjectRepository, ossStorageAdapter,
            parser, elementRepository);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void ownerPrincipal() {
        AuthContext.set(new UserPrincipal("U1", "u", "U", Set.of("STUDENT"), Set.of(), Set.of()));
    }

    private void readyDocx() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "报告", "REPORT", "READY", VER_ID)));
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(
                new DocumentVersion(VER_ID, DOC_ID, 1, "UPLOAD", OBJ_ID, "U1", "hash", "A")));
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.docx", null, "etag", "hash",
                100, "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "NONE", "ORIGINAL")));
    }

    @Test
    void lazilyParsesAndPersistsWhenEmpty() throws Exception {
        ownerPrincipal();
        readyDocx();
        when(elementRepository.countByVersion(VER_ID)).thenReturn(0);
        when(parser.parse(any())).thenReturn(List.of(
                new ElementDraft("H-0", "HEADING", "1", null, null, "绪论", "{\"paragraphIndex\":0}")));
        when(elementRepository.findByVersion(VER_ID)).thenReturn(List.of(
                new DocumentElement("E1", VER_ID, "H-0", "HEADING", "1", null, null, "绪论",
                        "{\"paragraphIndex\":0}")));
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("edudoc-struct-test", ".docx");
        java.nio.file.Files.writeString(temp, "fake");
        org.mockito.Mockito.doAnswer(invocation -> {
            java.nio.file.Files.copy(temp, invocation.getArgument(1),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return null;
        }).when(ossStorageAdapter).downloadTo(anyString(), any());

        List<ElementView> views = service.listElements(DOC_ID);

        assertEquals(1, views.size());
        assertEquals("HEADING", views.get(0).elementType());
        ArgumentCaptor<List<DocumentElement>> captor = ArgumentCaptor.forClass(List.class);
        verify(elementRepository).replace(eq(VER_ID), captor.capture());
        assertEquals("H-0", captor.getValue().get(0).getElementKey());
        java.nio.file.Files.deleteIfExists(temp);
    }

    @Test
    void cachedElementsSkipDownload() {
        ownerPrincipal();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "报告", "REPORT", "READY", VER_ID)));
        DocumentElement stored = new DocumentElement("E1", VER_ID, "H-0", "HEADING", "1",
                null, null, "绪论", "{\"paragraphIndex\":0}");
        when(elementRepository.countByVersion(VER_ID)).thenReturn(1);
        when(elementRepository.findByVersion(VER_ID)).thenReturn(List.of(stored));

        List<ElementView> views = service.listElements(DOC_ID);

        assertEquals(1, views.size());
        assertEquals("绪论", views.get(0).captionText());
        verify(ossStorageAdapter, never()).downloadTo(anyString(), any());
    }

    @Test
    void nonOwnerRejectedAsInvisible() {
        AuthContext.set(new UserPrincipal("U2", "v", "V", Set.of("STUDENT"), Set.of(), Set.of()));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "报告", "REPORT", "READY", VER_ID)));
        var e = assertThrows(CidasBusinessException.class, () -> service.listElements(DOC_ID));
        assertEquals(40401, e.errorCode().code());
    }

    @Test
    void nonDocxReturnsEmptyWithoutParsing() {
        ownerPrincipal();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(
                new Document(DOC_ID, null, "U1", "课件", "COURSEWARE", "READY", VER_ID)));
        when(versionRepository.findById(VER_ID)).thenReturn(Optional.of(
                new DocumentVersion(VER_ID, DOC_ID, 1, "UPLOAD", OBJ_ID, "U1", "hash", "A")));
        when(ossObjectRepository.findById(OBJ_ID)).thenReturn(Optional.of(new OssObject(
                OBJ_ID, "bucket", "cn-beijing", "edudoc/uploads/a.pdf", null, "etag", "hash",
                100, "application/pdf", "NONE", "ORIGINAL")));
        when(elementRepository.countByVersion(VER_ID)).thenReturn(0);

        assertEquals(List.of(), service.listElements(DOC_ID));
        verify(parser, never()).parse(any());
    }
}
