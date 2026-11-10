package com.cidas.edudoc.coursework.application.impl;

import com.cidas.edudoc.access.application.AuditService;
import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import com.cidas.edudoc.coursework.application.CourseworkJson;
import com.cidas.edudoc.coursework.application.TeacherCourseworkService;
import com.cidas.edudoc.coursework.domain.CourseworkRules;
import com.cidas.edudoc.coursework.domain.CourseworkRules.Grade;
import com.cidas.edudoc.coursework.domain.CourseworkRules.RubricItem;
import com.cidas.edudoc.coursework.domain.CourseworkRules.ScoreItem;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRepository.AssignmentCreate;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.AssignmentStats;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.MemberAccount;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.Submission;
import com.cidas.edudoc.coursework.infrastructure.CourseworkRow.TeacherCourse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教师侧作业域应用服务实现（06 切片 1）。
 *
 * <p>校验顺序照 06 契约：主键格式（{@code Ids.isHex16}）→ 课程范围（courseScope，越权 40301）→ 业务参数。
 * SQL 可见性由 XML 的 {@code teacherCourseScope} 片段按当前教师兜底，服务层再做显式范围校验双保险。
 */
@Service
@RequiredArgsConstructor
public class TeacherCourseworkServiceImpl implements TeacherCourseworkService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PRIORITY_LIMIT = 5;
    private static final Set<String> SUBMISSION_FORMATS = Set.of("DOCX", "PDF", "BOTH");

    /** 仅用于 rubric_json / score_json 的本地序列化（CourseworkJson 冻结、未提供这两个方向的读写）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CourseworkRepository repository;
    private final AuditService auditService;

    // ---------- 课程 ----------

    @Override
    public PageResult<CourseView> pageCourses(CourseQuery query) {
        UserPrincipal principal = AuthContext.require();
        int page = normalizePage(query.page());
        int pageSize = normalizePageSize(query.pageSize());
        PageResult<TeacherCourse> result = repository.pageTeacherCourses(principal.userId(),
                blankToNull(query.term()), blankToNull(query.status()), blankToNull(query.keyword()),
                page, pageSize);
        List<CourseView> items = result.items().stream()
                .map(TeacherCourseworkServiceImpl::toView)
                .toList();
        return new PageResult<>(items, result.total(), page, pageSize);
    }

    @Override
    @Transactional
    public CourseView createCourse(CourseCreateCommand command) {
        UserPrincipal principal = AuthContext.require();
        requireText(command.name(), "课程名称不能为空");
        requireText(command.code(), "课程代码不能为空");
        requireText(command.term(), "学期不能为空");
        String code = command.code().trim();
        String name = command.name().trim();
        String term = command.term().trim();
        // 重名（course_code + term_code）由仓储翻译成 40902
        String courseId = repository.createCourse(code, name, term, principal.userId());
        // 课程可见性以 course_member(TEACHER) 为准（teacherCourseScope / courseScope 同源），
        // 创建者必须自登记为成员，否则新课对本人也不可见
        repository.addMember(courseId, principal.userId(), "TEACHER", null);
        auditService.record("COURSE_CREATED", "COURSE", courseId, null, "200",
                Map.of("courseCode", code, "termCode", term));
        return new CourseView(courseId, code, name, term, principal.displayName(), 0, 0, null, "A", 0);
    }

    @Override
    @Transactional
    public ImportResult importMembers(String courseId, List<MemberImportRow> rows) {
        UserPrincipal principal = AuthContext.require();
        requireScope(principal, courseId);
        if (rows == null || rows.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "导入名单不能为空");
        }
        int created = 0;
        int activated = 0;
        int conflicts = 0;
        List<ImportFailure> failures = new ArrayList<>();
        for (MemberImportRow row : rows) {
            String accountNo = row.accountNo() == null ? "" : row.accountNo().trim();
            if (accountNo.isEmpty()) {
                failures.add(new ImportFailure(row.accountNo(), "学号为空"));
                continue;
            }
            MemberAccount user = repository.findUserByAccount(accountNo);
            if (user == null) {
                // 名单里无账号：建未激活账号并入组，待统一身份认证首登激活
                String userId = repository.createInactiveUser(accountNo, row.name());
                repository.addMember(courseId, userId, "STUDENT", row.group());
                activated++;
            } else if (repository.isCourseMember(courseId, user.getUserId())) {
                conflicts++;
            } else {
                repository.addMember(courseId, user.getUserId(), "STUDENT", row.group());
                created++;
            }
        }
        ImportResult result = new ImportResult(rows.size(), created, activated, conflicts, List.copyOf(failures));
        auditService.record("MEMBER_IMPORTED", "COURSE", courseId, null, "200",
                Map.of("total", result.total(), "created", created, "activated", activated,
                        "conflicts", conflicts, "failures", failures.size()));
        return result;
    }

    // ---------- 作业 ----------

    @Override
    public PageResult<AssignmentView> listAssignments(String courseId, String status,
                                                      Integer page, Integer pageSize) {
        UserPrincipal principal = AuthContext.require();
        String courseFilter = blankToNull(courseId);
        if (courseFilter != null) {
            requireScope(principal, courseFilter);
        }
        List<AssignmentView> all = repository
                .listAssignments(principal.userId(), courseFilter, blankToNull(status)).stream()
                .map(TeacherCourseworkServiceImpl::toView)
                .toList();
        return paginate(all, page, pageSize);
    }

    @Override
    @Transactional
    public CreatedAssignment createAssignment(AssignmentCreateCommand command) {
        UserPrincipal principal = AuthContext.require();
        requireScope(principal, command.courseId());
        requireText(command.title(), "作业标题不能为空");
        OffsetDateTime dueTime;
        try {
            dueTime = OffsetDateTime.parse(command.dueTime());
        } catch (RuntimeException e) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "截止时间格式不合法，需为 ISO-8601 带时区偏移");
        }
        List<RubricItem> rubric = validateRubric(command.rubric());
        if (!SUBMISSION_FORMATS.contains(command.submissionFormat())) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "提交格式仅支持 DOCX/PDF/BOTH");
        }
        // 缺省按 DDL 默认值 'N'：发布表单未暴露这两个开关时省略字段是合法的，不能因此 400
        String allowLate = command.allowLate() == null ? "N" : command.allowLate();
        String requireFormatCheck = command.requireFormatCheck() == null ? "N" : command.requireFormatCheck();
        requireYesNo(allowLate, "允许迟交仅支持 Y/N");
        requireYesNo(requireFormatCheck, "格式强校验仅支持 Y/N");
        String title = command.title().trim();
        String status = Boolean.TRUE.equals(command.draft()) ? "DRAFT" : "PUBLISHED";
        String assignmentId = repository.createAssignment(new AssignmentCreate(command.courseId(), title,
                command.requirement(), principal.userId(), dueTime, allowLate,
                command.submissionFormat(), requireFormatCheck, writeRubric(rubric), status));
        auditService.record("ASSIGNMENT_CREATED", "ASSIGNMENT", assignmentId, null, "200",
                Map.of("courseId", command.courseId(), "title", title, "status", status));
        return new CreatedAssignment(assignmentId, status);
    }

    @Override
    public UnsubmittedResult listUnsubmitted(String assignmentId) {
        UserPrincipal principal = AuthContext.require();
        // 越权检查保留（非本人课程 → 40301），但 GET 不再写审计：
        // 审查指出每次读名单都落一条 REMIND_UNSUBMITTED，预取或重放会刷审计。
        // 提醒动作与投递随切片 2 的消息生产者落地（届时改为显式 POST 动作端点）。
        loadScopedAssignment(principal, assignmentId);
        List<UnsubmittedStudent> items = repository.listUnsubmitted(assignmentId).stream()
                .map(row -> new UnsubmittedStudent(row.getAccountNo(), row.getName()))
                .toList();
        return new UnsubmittedResult(items);
    }

    // ---------- 提交与批改 ----------

    @Override
    public PageResult<SubmissionView> listSubmissions(String assignmentId, String status,
                                                      Integer page, Integer pageSize) {
        UserPrincipal principal = AuthContext.require();
        String assignmentFilter = blankToNull(assignmentId);
        if (assignmentFilter != null && !Ids.isHex16(assignmentFilter)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "assignmentId 格式不合法");
        }
        // 课程范围由 SQL teacherCourseScope 按当前教师过滤，越权查询自然得空集
        List<SubmissionView> all = repository
                .listSubmissions(principal.userId(), assignmentFilter, blankToNull(status)).stream()
                .map(TeacherCourseworkServiceImpl::toView)
                .toList();
        return paginate(all, page, pageSize);
    }

    @Override
    @Transactional
    public GradeResult gradeSubmission(String submissionId, GradeCommand command) {
        UserPrincipal principal = AuthContext.require();
        if (!Ids.isHex16(submissionId)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "submissionId 格式不合法");
        }
        Submission submission = repository.findSubmissionById(submissionId);
        if (submission == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "提交不存在");
        }
        AssignmentStats assignment = loadScopedAssignment(principal, submission.getAssignmentId());
        List<CourseworkJson.ScoreWire> wires = command.items() == null ? List.of()
                : command.items().stream()
                        .map(item -> new CourseworkJson.ScoreWire(item.name(), null, item.score()))
                        .toList();
        // 分项校验与总分都在领域规则内完成：总分服务端计算，不信任入参
        Grade grade = CourseworkRules.grade(CourseworkJson.readRubric(assignment.getRubricJson()),
                CourseworkJson.toScoreItems(wires));
        repository.gradeSubmission(submissionId, BigDecimal.valueOf(grade.total()), command.feedback(),
                CourseworkJson.writeScores(grade.items()), CourseworkRules.GRADED);
        auditService.record("SUBMISSION_GRADED", "SUBMISSION", submissionId, null, "200",
                Map.of("assignmentId", assignment.getId(), "score", grade.total()));
        return new GradeResult(grade.total(), CourseworkRules.GRADED);
    }

    // ---------- 工作台 ----------

    @Override
    public WorkbenchSummary workbenchSummary() {
        UserPrincipal principal = AuthContext.require();
        OffsetDateTime now = OffsetDateTime.now();
        List<PriorityView> priority = repository.listPriorities(principal.userId(), PRIORITY_LIMIT).stream()
                .map(row -> new PriorityView(row.getAssignmentId(), row.getCourseName(), row.getTitle(),
                        row.getDueTime(), row.getExpected(), row.getSubmitted(), row.getGraded(),
                        remainText(row.getDueTime(), now)))
                .toList();
        return new WorkbenchSummary(
                repository.countDueThisWeek(principal.userId(), now, now.plusDays(7)),
                repository.countPendingGrade(principal.userId()),
                repository.countFormatIssues(principal.userId()),
                // 协作统计随切片 3 开放，此前恒 0、不做假数
                0,
                priority);
    }

    // ---------- 私有工具 ----------

    /** 主键格式 + 课程范围校验（信任边界）：非法 40001、越权 40301。 */
    private static void requireScope(UserPrincipal principal, String courseIdHex) {
        if (!Ids.isHex16(courseIdHex)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "courseId 格式不合法");
        }
        boolean inScope = principal.courseScope().stream().anyMatch(courseIdHex::equalsIgnoreCase);
        if (!inScope) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "非本人任教课程");
        }
    }

    /** 按主键取作业并校验课程范围：不存在 40401、越权 40301。 */
    private AssignmentStats loadScopedAssignment(UserPrincipal principal, String assignmentIdHex) {
        if (!Ids.isHex16(assignmentIdHex)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "assignmentId 格式不合法");
        }
        AssignmentStats assignment = repository.findAssignment(assignmentIdHex);
        if (assignment == null) {
            throw new CidasBusinessException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        boolean inScope = principal.courseScope().stream()
                .anyMatch(course -> course.equalsIgnoreCase(assignment.getCourseId()));
        if (!inScope) {
            throw new CidasBusinessException(ErrorCode.FORBIDDEN, "非本人任教课程的作业");
        }
        return assignment;
    }

    /** 评分标准入参校验：非空、项名非空且不重复、满分为正整数。 */
    private static List<RubricItem> validateRubric(List<RubricInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "评分标准不能为空");
        }
        Set<String> names = new HashSet<>();
        List<RubricItem> rubric = new ArrayList<>(inputs.size());
        for (RubricInput input : inputs) {
            if (input.name() == null || input.name().isBlank()) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "评分项名称不能为空");
            }
            if (input.maxScore() == null || input.maxScore() <= 0) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                        "评分项「" + input.name() + "」满分须为正整数");
            }
            if (!names.add(input.name())) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "评分项重复：" + input.name());
            }
            rubric.add(new RubricItem(input.name(), input.maxScore()));
        }
        return rubric;
    }

    /** rubric 项列表 → rubric_json（与 {@code CourseworkJson.readRubric} 的 wire 形状互逆）。 */
    private static String writeRubric(List<RubricItem> rubric) {
        try {
            return MAPPER.writeValueAsString(rubric);
        } catch (JsonProcessingException e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "评分标准序列化失败");
        }
    }

    /** score_json → 分项数组（照 {@code CourseworkJson.readRubric} 的容错口径）；空列返回空数组。 */
    private static List<ScoreItem> readScores(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(MAPPER.readValue(json, CourseworkJson.ScoreWire[].class))
                    .map(wire -> new ScoreItem(wire.name(),
                            wire.maxScore() == null ? 0 : wire.maxScore(),
                            wire.score() == null ? 0 : wire.score()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "评分结果数据损坏，请重新批改");
        }
    }

    /** 格式结论派生：-1 未校验 → NONE；阻断 → BLOCK；仅告警 → WARN；否则 PASS。 */
    static String deriveFormat(long blockingCount, long warningCount) {
        if (blockingCount < 0) {
            return "NONE";
        }
        if (blockingCount > 0) {
            return "BLOCK";
        }
        return warningCount > 0 ? "WARN" : "PASS";
    }

    /** 剩余时间文案（天为单位）：逾期 → 「已逾期 N 天」，否则 → 「N 天后截止」。 */
    static String remainText(OffsetDateTime dueTime, OffsetDateTime now) {
        if (dueTime == null) {
            return "";
        }
        return now.isAfter(dueTime)
                ? "已逾期 " + ChronoUnit.DAYS.between(dueTime, now) + " 天"
                : ChronoUnit.DAYS.between(now, dueTime) + " 天后截止";
    }

    /** Mapper 未分页的作业/提交列表在服务层内存分页。 */
    private static <T> PageResult<T> paginate(List<T> all, Integer page, Integer pageSize) {
        int normalizedPage = normalizePage(page);
        int size = normalizePageSize(pageSize);
        int from = Math.min((normalizedPage - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), normalizedPage, size);
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /** 缺省 10；非法（&lt;1 或 &gt;100）回落默认。 */
    private static int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE ? DEFAULT_PAGE_SIZE : pageSize;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private static void requireYesNo(String value, String message) {
        if (!"Y".equals(value) && !"N".equals(value)) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static CourseView toView(TeacherCourse row) {
        return new CourseView(row.getId(), row.getCourseCode(), row.getCourseName(), row.getTermCode(),
                row.getOwnerName(), row.getGroups(), row.getStudents(), row.getTemplateName(),
                row.getStatus(), row.getDueWeek());
    }

    private static AssignmentView toView(AssignmentStats row) {
        return new AssignmentView(row.getId(), row.getCourseId(), row.getCourseName(), row.getTitle(),
                row.getDescription(), row.getDueTime(), row.getAllowLate(), row.getSubmissionFormat(),
                row.getRequireFormatCheck(), row.getStatus(), row.getExpected(), row.getSubmitted(),
                // 成员变动可能让 submitted 短暂超过 expected，负数无意义，钳到 0
                Math.max(0, row.getExpected() - row.getSubmitted()),
                row.getOverdue(), row.getGraded(), row.getFormatIssues(),
                CourseworkJson.readRubric(row.getRubricJson()));
    }

    private static SubmissionView toView(Submission row) {
        return new SubmissionView(row.getId(), row.getAssignmentId(), row.getAssignmentTitle(),
                row.getCourseName(), row.getStudentId(), row.getStudentName(), row.getAccountNo(),
                row.getDocumentId(), row.getDocumentTitle(), row.getVersionNo(),
                deriveFormat(row.getBlockingCount(), row.getWarningCount()),
                row.getStatus(), row.getScore(), row.getFeedback(), readScores(row.getScoreJson()),
                row.getAttemptNo(), row.getSubmittedAt());
    }
}
