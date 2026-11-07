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
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.FormatFixService;
import com.cidas.edudoc.template.application.FormatFixTxWriter;
import com.cidas.edudoc.template.domain.FixAction;
import com.cidas.edudoc.template.domain.RuleConfig;
import com.cidas.edudoc.template.domain.StyleSnapshot;
import com.cidas.edudoc.template.infrastructure.DocxStyleEditor;
import com.cidas.edudoc.template.infrastructure.FormatCheck;
import com.cidas.edudoc.template.infrastructure.FormatCheckRepository;
import com.cidas.edudoc.template.infrastructure.FormatIssue;
import com.cidas.edudoc.template.infrastructure.FormatIssueRepository;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一键修复编排（D-05）：校验后版本前移 → 40901；当前版本非 docx（03 润色 .txt 链）→ 40901 指引（R2）；
 * 修复面四类（R1）之外的问题行一律 skipped 不改；成版后自动复审（verifyAgain 伪代码）。
 */
@Service
@RequiredArgsConstructor
public class FormatFixServiceImpl implements FormatFixService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final OssObjectRepository ossObjectRepository;
    private final FormatCheckRepository checkRepository;
    private final FormatIssueRepository issueRepository;
    private final FormatRuleRepository ruleRepository;
    private final OssStorageAdapter ossStorageAdapter;
    private final DocxStyleEditor styleEditor;
    private final FormatFixTxWriter txWriter;
    private final OssProperties ossProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FixView fix(String checkId, List<String> issueIds) {
        if (!Ids.isHex16(checkId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "校验任务 ID 格式不合法");
        }
        FormatCheck check = checkRepository.findById(checkId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "校验任务不存在"));
        Document document = loadOwnedDocument(check.getDocumentId());
        if (!"PASSED".equals(check.getStatus()) && !"FAILED".equals(check.getStatus())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "校验任务尚未完成，无法修复");
        }
        if (!check.getDocumentVersionId().equalsIgnoreCase(document.getCurrentVersionId())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "文档已产生新版本，请重新发起校验后再修复");
        }
        DocumentVersion version = versionRepository.findById(check.getDocumentVersionId())
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.CONFLICT, "被校验版本不存在"));
        OssObject source = version.getSourceObjectId() == null ? null
                : ossObjectRepository.findById(version.getSourceObjectId()).orElse(null);
        if (source == null || !DOCX_MIME.equals(source.getMimeType())) {
            throw new CidasBusinessException(ErrorCode.CONFLICT,
                    "当前版本源文件不是 Word 文档（如润色派生文本版），格式修复仅支持 .docx 原件链");
        }

        // 终审 I3：非 hex 问题 ID 不进 HEXTORAW（S2 信任边界），直接计入 skipped
        List<String> requested = issueIds == null ? List.of() : issueIds.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        List<String> hexIds = requested.stream().filter(Ids::isHex16).toList();
        List<String> invalidIds = requested.stream().filter(id -> !Ids.isHex16(id)).toList();
        List<FormatIssue> selected = issueRepository.findByIds(hexIds);
        List<FormatIssue> fixable = new ArrayList<>();
        List<String> skipped = new ArrayList<>(invalidIds);
        for (String issueId : requested) {
            if (!Ids.isHex16(issueId)) {
                continue; // 已在 invalidIds 计入 skipped
            }
            FormatIssue issue = selected.stream().filter(i -> i.getId().equalsIgnoreCase(issueId))
                    .findFirst().orElse(null);
            if (issue == null || !issue.getFormatCheckId().equalsIgnoreCase(checkId)
                    || !"OPEN".equals(issue.getFixStatus()) || !"Y".equals(issue.getAutoFixable())) {
                skipped.add(issueId);
            } else {
                fixable.add(issue);
            }
        }
        if (fixable.isEmpty()) {
            return new FixView(null, null, null, List.of(), skipped);
        }

        Map<String, FormatRule> rules = new LinkedHashMap<>();
        ruleRepository.findByIds(fixable.stream().map(FormatIssue::getRuleId).filter(java.util.Objects::nonNull)
                .distinct().toList()).forEach(rule -> rules.put(rule.getId(), rule));
        List<FixAction> actions = new ArrayList<>();
        List<String> fixedIds = new ArrayList<>();
        List<String> stillSkipped = new ArrayList<>(skipped);
        for (FormatIssue issue : fixable) {
            FixAction action = toAction(issue, rules.get(issue.getRuleId()));
            if (action == null) {
                stillSkipped.add(issue.getId());
            } else {
                actions.add(action);
                fixedIds.add(issue.getId());
            }
        }
        if (actions.isEmpty()) {
            return new FixView(null, null, null, List.of(), stillSkipped);
        }

        byte[] fixedBytes = styleEditor.apply(download(source), actions);
        int versionNo = versionRepository.maxVersionNo(document.getId()) + 1;
        String objectKey = "edudoc/documents/%s/fix/%s-v%d.docx".formatted(
                monthDir(), document.getId(), versionNo);
        var info = ossStorageAdapter.putBytes(objectKey, fixedBytes, DOCX_MIME);
        String contentHash = sha256(fixedBytes);

        OssObject newObject = new OssObject(Ids.hex16(), ossProperties.bucket(), ossProperties.region(),
                objectKey, info.versionId(), info.etag(), contentHash, info.contentLength(), DOCX_MIME,
                info.encryption() == null ? "NONE" : info.encryption(), "ORIGINAL");
        DocumentVersion newVersion = new DocumentVersion(Ids.hex16(), document.getId(), versionNo, "EDIT",
                newObject.getId(), AuthContext.require().userId(), contentHash, "A");
        FormatCheck recheck = new FormatCheck();
        recheck.setDocumentId(document.getId());
        recheck.setTemplateVersionId(check.getTemplateVersionId());
        recheck.setRequestedBy(AuthContext.require().userId());
        recheck.setStatus("QUEUED");

        var outcome = txWriter.apply(new FormatFixTxWriter.FixRequest(newObject, newVersion, fixedIds,
                recheck, checkId, check.getDocumentVersionId()));
        return new FixView(outcome.versionId(), outcome.versionNo(), outcome.recheckId(),
                fixedIds, stillSkipped);
    }

    /**
     * 问题+规则 → 修复动作（密封变体）：fix 子树经 {@link RuleConfig} 同一解释（第五轮 P2-2 收口，
     * auto_fixable=Y 必有有效 fix 已由保存闸保证；此处对存量脏数据仍返回 null → skipped 回执明示）。
     */
    private FixAction toAction(FormatIssue issue, FormatRule rule) {
        if (rule == null || !issue.getRuleId().equals(rule.getId())) {
            return null;
        }
        try {
            RuleConfig view = RuleConfig.parse(rule.getRuleType(),
                    rule.getRuleConfigJson(), objectMapper).asFixView();
            return switch (rule.getRuleType()) {
                case "FONT" -> view.family() == null && view.eastAsia() == null
                        ? null : new FixAction.Font(paragraphIndex(issue), view.family(), view.eastAsia());
                case "SIZE" -> view.pt() == null
                        ? null : new FixAction.Size(paragraphIndex(issue), view.pt());
                case "LINE_SPACING" -> {
                    // 第四轮 P1-2：AUTO 倍数与固定磅值两种表达都可修复
                    if (view.multiplier() != null) {
                        yield new FixAction.Line(paragraphIndex(issue), "AUTO", view.multiplier());
                    }
                    StyleSnapshot.LineSpacing fixed = view.fixedLine();
                    yield fixed == null || fixed.value() == null ? null
                            : new FixAction.Line(paragraphIndex(issue), fixed.kind(), fixed.value());
                }
                case "MARGIN" -> {
                    // 第四轮 P1-1：width/height 纳入修复面（页边距+纸型一次写全，杜绝假闭环）
                    Map<String, Double> page = view.pageValues();
                    yield page.isEmpty() ? null : new FixAction.Page(page);
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private int paragraphIndex(FormatIssue issue) {
        try {
            JsonNode node = objectMapper.readTree(issue.getLocationRef());
            return node.path("paragraphIndex").asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    private byte[] download(OssObject object) {
        Path temp = null;
        try {
            temp = Files.createTempFile("edudoc-fix-", ".docx");
            ossStorageAdapter.downloadTo(object.getObjectKey(), temp);
            return Files.readAllBytes(temp);
        } catch (java.io.IOException e) {
            throw new CidasBusinessException(ErrorCode.SERVICE_UNAVAILABLE, "修复读取源文件失败",
                    true, Map.of());
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

    private Document loadOwnedDocument(String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
        if (!AuthContext.require().userId().equalsIgnoreCase(document.getOwnerId())) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private static String monthDir() {
        return LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
