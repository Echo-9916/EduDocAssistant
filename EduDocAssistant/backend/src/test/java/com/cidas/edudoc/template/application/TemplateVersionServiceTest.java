package com.cidas.edudoc.template.application;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.infrastructure.oss.OssProperties;
import com.cidas.edudoc.infrastructure.oss.OssStorageAdapter;
import com.cidas.edudoc.template.application.TemplateService.TemplateView;
import com.cidas.edudoc.template.application.TemplateVersionService.RuleCommand;
import com.cidas.edudoc.template.application.TemplateVersionService.TemplateVersionView;
import com.cidas.edudoc.template.application.TemplateVersionTxWriter;
import com.cidas.edudoc.template.application.impl.TemplateVersionServiceImpl;
import com.cidas.edudoc.template.domain.RuleDraft;
import com.cidas.edudoc.template.domain.TemplateParseException;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import com.cidas.edudoc.template.infrastructure.FormatRuleRepository;
import com.cidas.edudoc.template.infrastructure.Template;
import com.cidas.edudoc.template.infrastructure.TemplateRepository;
import com.cidas.edudoc.template.infrastructure.TemplateRuleParser;
import com.cidas.edudoc.template.infrastructure.TemplateVersion;
import com.cidas.edudoc.template.infrastructure.TemplateVersionRepository;
import com.cidas.edudoc.document.infrastructure.OssObject;
import com.cidas.edudoc.template.infrastructure.FormatRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-02 模板版本：解析入库、发布门、历史版本不可变（UT-13 前半）。 */
class TemplateVersionServiceTest {

    private static final String TPL = "A1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String VER = "B1B2C3D4E5F60708090A0B0C0D0E0F10";
    private static final String COURSE = "C1B2C3D4E5F60708090A0B0C0D0E0F10";

    private final TemplateRepository templateRepository = mock(TemplateRepository.class);
    private final TemplateVersionRepository versionRepository = mock(TemplateVersionRepository.class);
    private final FormatRuleRepository ruleRepository = mock(FormatRuleRepository.class);
    private final com.cidas.edudoc.document.infrastructure.OssObjectRepository realOssRepo =
            mock(com.cidas.edudoc.document.infrastructure.OssObjectRepository.class);
    private final OssStorageAdapter ossStorageAdapter = mock(OssStorageAdapter.class);
    private final TemplateRuleParser parser = mock(TemplateRuleParser.class);
    private final AuditService auditService = mock(AuditService.class);
    private final OssProperties ossProperties = new OssProperties("endpoint", "cn-beijing", "cidas-oss-bucket",
            "ak", "sk", "arn", "sts", "edudoc/uploads", 120, 900);
    private final TemplateVersionTxWriter txWriter = new TemplateVersionTxWriter(
            realOssRepo, versionRepository, ruleRepository, templateRepository, auditService);
    private final TemplateVersionService service = new TemplateVersionServiceImpl(
            templateRepository, versionRepository, ruleRepository,
            ossStorageAdapter, parser, txWriter, ossProperties);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void teacherPrincipal() {
        AuthContext.set(new UserPrincipal("T1", "t", "T", Set.of("TEACHER"), Set.of(), Set.of()));
    }

    private Template template(String status) {
        return new Template(TPL, COURSE, "报告模板", null, "T1", null, status);
    }

    private TemplateVersion version(String validation, String publishedAt) {
        return new TemplateVersion(VER, TPL, 2, null, validation, null, publishedAt, null, null);
    }

    @Test
    void createWithFileParsesRulesAndPersistsVersionValid() throws Exception {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.maxVersionNo(TPL)).thenReturn(1);
        when(parser.parse(any())).thenReturn(List.of(
                new RuleDraft("FONT", "HEADING_1", "BLOCKER", "Y", "{\"expect\":{\"family\":\"黑体\"}}", 10)));
        when(ossStorageAdapter.putBytes(anyString(), any(), anyString()))
                .thenReturn(new OssStorageAdapter.ObjectInfo(10, "ETAG", null, "SSE-OSS"));

        TemplateVersionView view = service.create(TPL, "首版", "t.docx".getBytes(), "t.docx");

        assertEquals(2, view.versionNo());
        assertEquals("VALID", view.validationStatus());
        ArgumentCaptor<TemplateVersion> captor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(versionRepository).insert(captor.capture());
        assertEquals("VALID", captor.getValue().getValidationStatus());
        assertNotNull(captor.getValue().getSourceObjectId());
        ArgumentCaptor<List<FormatRule>> rules = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).insertBatch(rules.capture());
        assertEquals("FONT", rules.getValue().get(0).getRuleType());
        assertEquals(10, rules.getValue().get(0).getRuleOrder());
    }

    /** 复审 P2-3：模板版本必须带 .docx 源文件（D-02 契约「上传并解析」，追溯性不回退）。 */
    @Test
    void createWithoutFileRejected() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));

        var e = assertThrows(CidasBusinessException.class, () -> service.create(TPL, "手工规则", null, null));
        assertEquals(40001, e.errorCode().code());
        verify(ossStorageAdapter, never()).putBytes(anyString(), any(), anyString());
        verify(versionRepository, never()).insert(any());
    }

    @Test
    void parseFailurePersistsInvalidVersion() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.maxVersionNo(TPL)).thenReturn(1);
        when(parser.parse(any())).thenThrow(new TemplateParseException("坏文件"));
        when(ossStorageAdapter.putBytes(anyString(), any(), anyString()))
                .thenReturn(new OssStorageAdapter.ObjectInfo(10, "ETAG", null, "SSE-OSS"));

        TemplateVersionView view = service.create(TPL, null, "bad.docx".getBytes(), "bad.docx");

        assertEquals("INVALID", view.validationStatus());
        // 终审 Minor：解析失败原因经响应回显（表 3-23 无承载列，D-09 表单展示）
        assertNotNull(view.failureReason());
        verify(ruleRepository, never()).insertBatch(any());
    }

    /** 终审 Minor：并发建版本撞 uk_template_version_1 → 40901 而非 500。 */
    @Test
    void concurrentVersionNoMapsToConflict() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.maxVersionNo(TPL)).thenReturn(1);
        when(parser.parse(any())).thenReturn(List.of());
        when(ossStorageAdapter.putBytes(anyString(), any(), anyString()))
                .thenReturn(new OssStorageAdapter.ObjectInfo(10, "ETAG", null, "SSE-OSS"));
        doThrow(new org.springframework.dao.DuplicateKeyException("uk_template_version_1"))
                .when(versionRepository).insert(any());

        var e = assertThrows(CidasBusinessException.class,
                () -> service.create(TPL, null, "t.docx".getBytes(), "t.docx"));
        assertEquals(40901, e.errorCode().code());
    }

    /** 终审 Minor：publish 的 markPublished 带 published_at IS NULL 守卫，输者 40901（双发布后写者胜的根因）。 */
    @Test
    void publishLosingCasMapsToConflict() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("P")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("VALID", null)));
        when(versionRepository.markPublished(VER, "T1")).thenReturn(false);

        var e = assertThrows(CidasBusinessException.class, () -> service.publish(TPL, VER));
        assertEquals(40901, e.errorCode().code());
        verify(templateRepository, never()).updateCurrentVersion(any(), any());
    }

    /** 终审 Minor：listVersions 的规则数走行内子查询，不再每版本一次 findByVersion（N+1）。 */
    @Test
    void listVersionsReadsRuleCountFromRow() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("P")));
        when(versionRepository.listByTemplate(TPL)).thenReturn(List.of(
                new TemplateVersion(VER, TPL, 2, null, "VALID", "T1", "2026-11-06T00:00:00", null, 7)));

        var views = service.listVersions(TPL);

        assertEquals(7, views.get(0).ruleCount());
        verify(ruleRepository, never()).findByVersion(any());
    }

    @Test
    void replaceRulesRejectsPublishedVersion() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("P")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("VALID", "2026-11-06T00:00:00")));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(rule("FONT"))));
        assertEquals(40901, e.errorCode().code());
    }

    @Test
    void replaceRulesPersistsAndMarksValid() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));

        service.replaceRules(TPL, VER, List.of(rule("FONT"), rule("SIZE")));

        verify(ruleRepository).deleteByVersion(VER);
        verify(ruleRepository).insertBatch(any());
        verify(versionRepository).updateValidation(VER, "VALID");
        verify(auditService).record(eq("TEMPLATE_RULES_UPDATED"), eq("TEMPLATE_VERSION"), eq(VER),
                any(), eq("200"), any());
    }

    /** 终审 I6：targetSelector 必须在词表内（否则校验静默失效 + 按模板新建 Integer.parseInt 崩）。 */
    @Test
    void replaceRulesRejectsIllegalSelector() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER,
                        List.of(new RuleCommand("FONT", "HEADING_X", "BLOCKER", "Y", "{\"expect\":{}}", 10))));
        assertEquals(40001, e.errorCode().code());
        verify(ruleRepository, never()).deleteByVersion(any());
    }

    /** 终审 I6：auto_fixable=Y 只对可修复规则类型允许（R1 修复面四类）。 */
    @Test
    void replaceRulesRejectsFixableFlagOnNonFixableType() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(
                        new RuleCommand("CAPTION", "FIGURE_CAPTION", "WARNING", "Y", "{\"expect\":{}}", 20))));
        assertEquals(40001, e.errorCode().code());
    }

    /** 复审 P2-4：CAPTION 规则 pattern 非法正则在保存阶段拒绝，不得标记 VALID 后静默失效。 */
    @Test
    void replaceRulesRejectsInvalidCaptionRegex() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(new RuleCommand("CAPTION", "FIGURE_CAPTION",
                        "WARNING", "N", "{\"expect\":{\"pattern\":\"^[(\"}}", 20))));
        assertEquals(40001, e.errorCode().code());
        verify(ruleRepository, never()).deleteByVersion(any());
    }

    @Test
    void replaceRulesEmptyKeepsVersionPending() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("INVALID", null)));

        service.replaceRules(TPL, VER, List.of());

        verify(versionRepository).updateValidation(VER, "PENDING");
    }

    @Test
    void publishRejectsInvalidVersion() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("INVALID", null)));
        var e = assertThrows(CidasBusinessException.class, () -> service.publish(TPL, VER));
        assertEquals(40901, e.errorCode().code());
        verify(templateRepository, never()).updateCurrentVersion(any(), any());
    }

    @Test
    void publishHappyPathAdvancesCurrentAndAudits() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("P")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("VALID", null)));
        when(versionRepository.markPublished(VER, "T1")).thenReturn(true);

        service.publish(TPL, VER);

        verify(versionRepository).markPublished(eq(VER), eq("T1"));
        verify(templateRepository).updateCurrentVersion(TPL, VER);
        verify(templateRepository).updateStatus(TPL, "P");
        verify(auditService).record(eq("TEMPLATE_PUBLISHED"), eq("TEMPLATE"), eq(TPL), any(), eq("200"), any());
    }

    @Test
    void publishTwiceRejected() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("P")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("VALID", "2026-11-06T00:00:00")));
        var e = assertThrows(CidasBusinessException.class, () -> service.publish(TPL, VER));
        assertEquals(40901, e.errorCode().code());
    }

    @Test
    void versionOfOtherTemplateRejected() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(
                new TemplateVersion(VER, "F1B2C3D4E5F60708090A0B0C0D0E0F10", 1, null, "VALID", null, null, null, null)));
        var e = assertThrows(CidasBusinessException.class, () -> service.publish(TPL, VER));
        assertEquals(40401, e.errorCode().code());
    }

    @Test
    void studentCannotReachManagePath() {
        AuthContext.set(new UserPrincipal("S1", "s", "S", Set.of("STUDENT"), Set.of(), Set.of(COURSE)));
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        var e = assertThrows(CidasBusinessException.class, () -> service.publish(TPL, VER));
        assertEquals(40401, e.errorCode().code());
    }

    /** 1106 审查 P2-1：规则读取端点——管理方按版本读全量规则。 */
    @Test
    void listRulesReturnsVersionRules() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("P")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("VALID", null)));
        when(ruleRepository.findByVersion(VER)).thenReturn(List.of(
                new FormatRule("R1", VER, "FONT", "HEADING_1", "BLOCKER", "Y",
                        "{\"expect\":{\"family\":\"黑体\"}}", 10)));

        var rules = service.listRules(TPL, VER);

        assertEquals(1, rules.size());
        assertEquals("FONT", rules.get(0).ruleType());
        assertEquals("{\"expect\":{\"family\":\"黑体\"}}", rules.get(0).ruleConfigJson());
    }

    /** 1106 审查 P2-1：学生 current-rules——课程内已发布可读，门外 40401，未发布 40901。 */
    @Test
    void studentReadsCurrentRulesWithinCourse() {
        AuthContext.set(new UserPrincipal("S1", "s", "S", Set.of("STUDENT"), Set.of(),
                Set.of("C1B2C3D4E5F60708090A0B0C0D0E0F10")));
        Template t = template("P");
        t.setCourseId("C1B2C3D4E5F60708090A0B0C0D0E0F10");
        t.setCurrentVersionId(VER);
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(t));
        when(ruleRepository.findByVersion(VER)).thenReturn(List.of(
                new FormatRule("R1", VER, "SIZE", "BODY", "WARNING", "Y", "{\"expect\":{\"pt\":12}}", 11)));

        assertEquals(1, service.currentRules(TPL).size());
    }

    @Test
    void studentOutsideCourseCannotReadCurrentRules() {
        AuthContext.set(new UserPrincipal("S2", "s", "S", Set.of("STUDENT"), Set.of(), Set.of()));
        Template t = template("P");
        t.setCurrentVersionId(VER);
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(t));
        var e = assertThrows(CidasBusinessException.class, () -> service.currentRules(TPL));
        assertEquals(40401, e.errorCode().code());
    }

    @Test
    void unpublishedTemplateHasNoCurrentRules() {
        AuthContext.set(new UserPrincipal("S1", "s", "S", Set.of("STUDENT"), Set.of(), Set.of(COURSE)));
        Template t = template("D");
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(t));
        var e = assertThrows(CidasBusinessException.class, () -> service.currentRules(TPL));
        assertEquals(40901, e.errorCode().code());
    }

    /** 第五轮 P2-1：键存在但语义非法（负值/字符串布尔/perChapter=false 无 pattern/lineRule 缺 pt）保存即拒。 */
    @Test
    void replaceRulesRejectsSemanticallyInvalidExpect() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        String[][] badCases = {
                {"SIZE", "HEADING_1", "{\"expect\":{\"pt\":-1}}"},                    // 负值
                {"CAPTION", "FIGURE_CAPTION", "{\"expect\":{\"perChapter\":false}}"},  // 空操作
                {"LINE_SPACING", "HEADING_1", "{\"expect\":{\"lineRule\":\"exact\"}}"},// 固定缺 pt
                {"MARGIN", "PAGE", "{\"expect\":{\"width\":0}}"},                       // 零值
        };
        for (String[] bad : badCases) {
            var e = assertThrows(CidasBusinessException.class, () -> service.replaceRules(TPL, VER,
                    List.of(new RuleCommand(bad[0], bad[1], "BLOCKER", "N", bad[2], 10))));
            assertEquals(40001, e.errorCode().code(), () -> "应拒绝 " + bad[2]);
        }
    }

    /** 第五轮 P2-1：present 字符串布尔被拒（必须 JSON 布尔）。 */
    @Test
    void replaceRulesRejectsStringBooleanPresent() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(new RuleCommand("HEADER_FOOTER", "HEADER",
                        "WARNING", "N", "{\"expect\":{\"present\":\"false\"}}", 14))));
        assertEquals(40001, e.errorCode().code());
    }

    /** 第五轮 P2-2：auto_fixable=Y 但 fix 缺失/无效 → 保存即拒（前端不会再出现「可勾选但实际 skipped」）。 */
    @Test
    void replaceRulesRejectsFixableWithoutFixValues() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(new RuleCommand("FONT", "BODY",
                        "WARNING", "Y", "{\"expect\":{\"family\":\"Arial\"}}", 10))));
        assertEquals(40001, e.errorCode().code());

        // fix 有效时放行
        service.replaceRules(TPL, VER, List.of(new RuleCommand("FONT", "BODY",
                "WARNING", "Y",
                "{\"expect\":{\"family\":\"Arial\"},\"fix\":{\"family\":\"Arial\"}}", 10)));
        verify(ruleRepository).insertBatch(any());
    }

    private static RuleCommand rule(String type) {
        return new RuleCommand(type, "HEADING_1", "BLOCKER",
                "FONT".equals(type) || "SIZE".equals(type) ? "Y" : "N", configFor(type), 10);
    }

    private static String configFor(String type) {
        return switch (type) {
            case "FONT" -> "{\"expect\":{\"family\":\"黑体\"},\"fix\":{\"family\":\"黑体\"}}";
            case "SIZE" -> "{\"expect\":{\"pt\":16,\"tolerance\":{\"pt\":0.5}},\"fix\":{\"pt\":16}}";
            case "LINE_SPACING" -> "{\"expect\":{\"multiplier\":1.5},\"fix\":{\"multiplier\":1.5}}";
            default -> "{\"expect\":{}}";
        };
    }

    /** 第四轮同类收口：LINE_SPACING 期望键集不可解析（无 multiplier 也无 lineRule）保存即拒。 */
    @Test
    void replaceRulesRejectsLineSpacingWithoutKeys() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(new RuleCommand("LINE_SPACING", "HEADING_1",
                        "BLOCKER", "Y", "{\"expect\":{}}", 12))));
        assertEquals(40001, e.errorCode().code());
    }

    /** 固定行距表达（lineRule+pt）是合法期望，不得被键集校验误拒。 */
    @Test
    void replaceRulesAcceptsFixedLineSpacing() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));

        service.replaceRules(TPL, VER, List.of(new RuleCommand("LINE_SPACING", "HEADING_1",
                "BLOCKER", "Y",
                "{\"expect\":{\"lineRule\":\"exact\",\"pt\":20},\"fix\":{\"lineRule\":\"exact\",\"pt\":20}}",
                12)));

        verify(ruleRepository).insertBatch(any());
    }

    /** MARGIN 期望全空（无任何边/宽高键）保存即拒。 */
    @Test
    void replaceRulesRejectsMarginWithoutKeys() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(new RuleCommand("MARGIN", "PAGE",
                        "BLOCKER", "Y", "{\"expect\":{\"unit\":\"mm\"}}", 13))));
        assertEquals(40001, e.errorCode().code());
    }

    /** FONT 两槽皆缺（expect 空 family/eastAsia）保存即拒。 */
    @Test
    void replaceRulesRejectsFontWithoutSlots() {
        teacherPrincipal();
        when(templateRepository.findById(TPL)).thenReturn(Optional.of(template("D")));
        when(versionRepository.findById(VER)).thenReturn(Optional.of(version("PENDING", null)));
        var e = assertThrows(CidasBusinessException.class,
                () -> service.replaceRules(TPL, VER, List.of(new RuleCommand("FONT", "BODY",
                        "WARNING", "Y", "{\"expect\":{\"size\":3}}", 10))));
        assertEquals(40001, e.errorCode().code());
    }
}
