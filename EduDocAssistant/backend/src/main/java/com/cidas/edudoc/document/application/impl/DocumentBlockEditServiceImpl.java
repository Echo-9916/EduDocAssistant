package com.cidas.edudoc.document.application.impl;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.document.application.DocumentBlockEditService;
import com.cidas.edudoc.document.domain.ChunkingResult;
import com.cidas.edudoc.document.domain.ChunkingService;
import com.cidas.edudoc.document.domain.TextBlock;
import com.cidas.edudoc.document.infrastructure.Document;
import com.cidas.edudoc.document.infrastructure.DocumentRepository;
import com.cidas.edudoc.document.infrastructure.ParseProperties;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 块编辑编排：校验 + 派生文本对象写入 OSS；DB 五写在 {@link DocumentBlockEditTxService}。 */
@Service
@RequiredArgsConstructor
public class DocumentBlockEditServiceImpl implements DocumentBlockEditService {

    private static final Set<String> TYPES = Set.of("H1", "H2", "BODY", "CODE");
    private static final Set<String> CODE_LANGS = Set.of("java", "cpp", "python", "sql");
    private static final int MAX_BLOCKS = 1000;
    private static final int MAX_TEXT_CHARS = 2_000_000;

    private final DocumentRepository documentRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final OssProperties ossProperties;
    private final ParseProperties parseProperties;
    private final DocumentBlockEditTxService txService;

    @Override
    public Result save(String documentId, String baseVersionId, List<Block> blocks) {
        if (!Ids.isHex16(documentId) || !Ids.isHex16(baseVersionId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档或版本 ID 格式不合法");
        }
        String userId = AuthContext.require().userId();
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!userId.equalsIgnoreCase(doc.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!baseVersionId.equalsIgnoreCase(doc.getCurrentVersionId())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档已被更新，请刷新后重试");
        }
        if (blocks == null || blocks.isEmpty() || blocks.size() > MAX_BLOCKS) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "块数量不合法");
        }
        List<TextBlock> textBlocks = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        int total = 0;
        for (Block block : blocks) {
            String type = block == null || block.type() == null ? "" : block.type().trim().toUpperCase(Locale.ROOT);
            if (!TYPES.contains(type)) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "块类型仅支持 H1、H2、BODY、CODE");
            }
            String text = block.text() == null ? "" : block.text();
            total += text.length();
            if (total > MAX_TEXT_CHARS) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档文本超过上限");
            }
            String kind = switch (type) {
                case "H1", "H2" -> TextBlock.HEADING;
                case "CODE" -> TextBlock.CODE;
                default -> TextBlock.PARAGRAPH;
            };
            Integer level = "H1".equals(type) ? Integer.valueOf(1)
                    : "H2".equals(type) ? Integer.valueOf(2) : null;
            String lang = null;
            if ("CODE".equals(type)) {
                lang = block.lang() == null || block.lang().isBlank()
                        ? "java" : block.lang().trim().toLowerCase(Locale.ROOT);
                if (!CODE_LANGS.contains(lang)) {
                    throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                            "代码语言仅支持 java、cpp、python、sql");
                }
            }
            textBlocks.add(new TextBlock(text, null, 0, kind, level, null, null, null, null, lang));
            fullText.append(text).append('\n');
        }
        String text = fullText.toString().stripTrailing();
        if (text.isBlank()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档内容不能为空");
        }
        ChunkingResult chunking = new ChunkingService(parseProperties.chunkSize()).chunk(textBlocks);
        if (chunking.chunks().isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "文档内容不能为空");
        }
        String objectKey = ossProperties.objectKeyPrefix() + "/derived/" + doc.getId()
                + "/" + Ids.hex16() + ".txt";
        OssStorageAdapter.ObjectInfo info = ossStorageAdapter.putText(objectKey, text);
        OssObject derived = new OssObject(Ids.hex16(), ossProperties.bucket(), ossProperties.region(),
                objectKey, info.versionId(), info.etag(), sha256Hex(text), info.contentLength(),
                "text/plain", info.encryption(), "PARSED");
        return txService.save(doc, derived, userId, text, chunking, blocks.size());
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}