package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.application.DocumentImageService;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentImage;
import com.cidas.edudoc.document.infrastructure.DocumentImageRepository;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.DocumentShareRepository;
import com.cidas.edudoc.document.infrastructure.DocumentShareRow;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 文档图片实现：图片落私有 OSS，浏览器通过带鉴权的代理端点取流（`<img>` 不直接持有 OSS 凭据）。
 * 只接受 PNG/JPEG/GIF/WEBP，10MB 上限；类型以文件魔数为准，不信任客户端自报 MIME。
 */
@Service
@RequiredArgsConstructor
public class DocumentImageServiceImpl implements DocumentImageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";
    private static final String GIF = "image/gif";
    private static final String WEBP = "image/webp";

    private final DocumentRepository documentRepository;
    private final DocumentImageRepository imageRepository;
    private final DocumentShareRepository shareRepository;
    private final CollaborationService collaborationService;
    private final OssStorageAdapter ossStorageAdapter;
    private final OssProperties ossProperties;
    private final AuditService auditService;

    @Override
    @Transactional
    public ImageView upload(String documentId, Upload upload) {
        if (!Ids.isHex16(documentId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档 ID 格式不合法");
        }
        if (upload == null || upload.bytes() == null || upload.bytes().length == 0) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "图片不能为空");
        }
        if (upload.bytes().length > MAX_IMAGE_BYTES) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "图片不能超过 10MB");
        }
        String mime = sniff(upload.bytes());
        if (mime == null) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "仅支持 PNG/JPEG/GIF/WEBP 图片");
        }
        String userId = AuthContext.require().userId();
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!userId.equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        String imageId = Ids.hex16();
        String objectKey = ossProperties.objectKeyPrefix() + "/images/" + doc.getId()
                + "/" + imageId + "." + extension(mime);
        ossStorageAdapter.putBytes(objectKey, upload.bytes(), mime);
        String fileName = upload.fileName() == null || upload.fileName().isBlank()
                ? imageId + "." + extension(mime) : upload.fileName().trim();
        imageRepository.insert(new DocumentImage(imageId, doc.getId(), userId, fileName, mime,
                upload.bytes().length, objectKey));
        auditService.record("DOCUMENT_IMAGE_UPLOADED", "DOCUMENT", doc.getId(), doc.getId(), "200",
                Map.of("imageId", imageId, "mimeType", mime, "sizeBytes", upload.bytes().length));
        return new ImageView(imageId, fileName, mime, upload.bytes().length);
    }

    @Override
    public ImageContent load(String documentId, String imageId) {
        if (!Ids.isHex16(documentId) || !Ids.isHex16(imageId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "图片 ID 格式不合法");
        }
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        String userId = AuthContext.require().userId();
        if (!userId.equalsIgnoreCase(doc.getOwnerId())
                && !collaborationService.isCollaborator(documentId, userId)) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        return read(documentId, imageId);
    }

    @Override
    public ImageContent loadPublic(String token, String imageId) {
        if (token == null || token.isBlank() || token.length() > 128 || !Ids.isHex16(imageId)) {
            throw new CidasBusinessException(ErrorCode.SHARE_LINK_EXPIRED);
        }
        DocumentShareRow share = shareRepository.findByToken(token.trim());
        if (share == null || !"A".equals(share.getStatus())) {
            throw new CidasBusinessException(ErrorCode.SHARE_LINK_EXPIRED);
        }
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(OffsetDateTime.now())) {
            shareRepository.markExpired(share.getId());
            throw new CidasBusinessException(ErrorCode.SHARE_LINK_EXPIRED);
        }
        return read(share.getDocumentId(), imageId);
    }

    private ImageContent read(String documentIdHex, String imageIdHex) {
        DocumentImage image = imageRepository.findByIdAndDocument(imageIdHex, documentIdHex);
        if (image == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        byte[] bytes = ossStorageAdapter.readBytes(image.getObjectKey(), MAX_IMAGE_BYTES);
        return new ImageContent(image.getFileName(), image.getMimeType(), bytes);
    }

    /** 文件魔数识别（PNG/JPEG/GIF/WEBP），返回 null 表示不是支持的图片。 */
    private static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return PNG;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return GIF;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return WEBP;
        }
        return null;
    }

    private static String extension(String mime) {
        return switch (mime) {
            case JPEG -> "jpg";
            case GIF -> "gif";
            case WEBP -> "webp";
            default -> "png";
        };
    }
}