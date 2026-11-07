package com.cidas.edudoc.template.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentVersion;
import com.cidas.edudoc.document.infrastructure.DocumentVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.document.infrastructure.OssObjectRepository;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.DocumentStructureService;
import com.cidas.edudoc.template.domain.ElementDraft;
import com.cidas.edudoc.template.infrastructure.DocxStructureParser;
import com.cidas.edudoc.template.infrastructure.DocumentElement;
import com.cidas.edudoc.template.infrastructure.DocumentElementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 结构树懒解析（D-03）：首读触发「下载 → 解析 → 整版覆盖」，此后命中库内数据；
 * 非 docx 当前版本返回空清单（PDF 结构树本期不做，J1 口径）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStructureServiceImpl implements DocumentStructureService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final OssObjectRepository ossObjectRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final DocxStructureParser structureParser;
    private final DocumentElementRepository elementRepository;

    @Override
    public List<ElementView> listElements(String documentId) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!AuthContext.require().userId().equalsIgnoreCase(document.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        String versionId = document.getCurrentVersionId();
        if (elementRepository.countByVersion(versionId) > 0) {
            return elementRepository.findByVersion(versionId).stream()
                    .map(DocumentStructureServiceImpl::toView)
                    .toList();
        }
        DocumentVersion version = versionRepository.findById(versionId).orElse(null);
        OssObject object = version == null || version.getSourceObjectId() == null ? null
                : ossObjectRepository.findById(version.getSourceObjectId()).orElse(null);
        if (object == null || !DOCX_MIME.equals(object.getMimeType())) {
            return List.of();
        }
        parseAndPersist(download(object), versionId);
        return elementRepository.findByVersion(versionId).stream()
                .map(DocumentStructureServiceImpl::toView)
                .toList();
    }

    @Override
    public int parseAndPersist(byte[] docxBytes, String versionIdHex) {
        List<ElementDraft> drafts = structureParser.parse(docxBytes);
        List<DocumentElement> entities = drafts.stream()
                .map(draft -> new DocumentElement(Ids.hex16(), versionIdHex, draft.elementKey(),
                        draft.elementType(), draft.chapterNo(), draft.sequenceNo(), draft.displayLabel(),
                        draft.captionText(), draft.locationJson()))
                .toList();
        elementRepository.replace(versionIdHex, entities);
        return entities.size();
    }

    private byte[] download(OssObject object) {
        Path temp = null;
        try {
            temp = Files.createTempFile("edudoc-structure-", ".docx");
            ossStorageAdapter.downloadTo(object.getObjectKey(), temp);
            return Files.readAllBytes(temp);
        } catch (java.io.IOException e) {
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE, "结构树解析读取文件失败",
                    true, java.util.Map.of());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (java.io.IOException ignored) {
                    // 临时文件清理失败不影响业务
                }
            }
        }
    }

    private static ElementView toView(DocumentElement element) {
        return new ElementView(element.getElementKey(), element.getElementType(), element.getChapterNo(),
                element.getSequenceNo(), element.getDisplayLabel(), element.getCaptionText(),
                element.getLocationJson());
    }
}
