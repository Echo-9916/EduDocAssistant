package com.cidas.edudoc.coursework.domain;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 作业与提交的业务规则（纯函数，不碰库）：迟交判定、学生视角状态派生、分项评分校验与总分。
 *
 * <p>时间一律用带偏移量的 {@link OffsetDateTime}：库列为 {@code TIMESTAMP(6) WITH TIME ZONE}，
 * 用 LocalDateTime 比较会在 +08:00 边界上差一天（06 计划 Review Focus 第 1 条）。
 */
public final class CourseworkRules {

    private CourseworkRules() {
    }

    /** 学生视角状态：仅 PUBLISHED/CLOSED 作业可见，DRAFT 由 SQL 过滤。 */
    public static final String PENDING = "PENDING";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String GRADED = "GRADED";
    public static final String OVERDUE = "OVERDUE";

    public enum SubmissionOutcome { SUBMITTED, LATE }

    /**
     * 提交结果判定：作业已关闭 → 40901；过截止且不允许迟交 → 40901；过截止允许迟交 → LATE。
     */
    public static SubmissionOutcome submissionOutcome(String assignmentStatus, OffsetDateTime dueTime,
                                                      boolean allowLate, OffsetDateTime now) {
        if ("CLOSED".equals(assignmentStatus)) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "作业已关闭，不能提交");
        }
        boolean overdue = dueTime != null && now.isAfter(dueTime);
        if (overdue && !allowLate) {
            throw new CidasBusinessException(ErrorCode.CONFLICT, "已过截止时间且不允许迟交");
        }
        return overdue ? SubmissionOutcome.LATE : SubmissionOutcome.SUBMITTED;
    }

    /** 学生作业状态派生，优先级 GRADED &gt; SUBMITTED &gt; OVERDUE &gt; PENDING。 */
    public static String studentStatus(String submissionStatus, OffsetDateTime dueTime, OffsetDateTime now) {
        if ("GRADED".equals(submissionStatus)) {
            return GRADED;
        }
        if (submissionStatus != null && !submissionStatus.isBlank()) {
            return SUBMITTED;
        }
        return dueTime != null && now.isAfter(dueTime) ? OVERDUE : PENDING;
    }

    /** 评分标准项（存 edudoc_assignment.rubric_json）。 */
    public record RubricItem(String name, int maxScore) {
    }

    /** 分项得分（存 edudoc_submission.score_json）。 */
    public record ScoreItem(String name, int maxScore, int score) {
    }

    /**
     * 校验教师提交的评分：项名必须命中评分标准、不重复、单项不越界、必须给全所有评分项；
     * 返回按标准顺序归一化后的分项与总分（总分服务端算，不信任前端）。
     */
    public static Grade grade(List<RubricItem> rubric, List<ScoreItem> submitted) {
        if (rubric.isEmpty()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "该作业未配置评分标准");
        }
        List<ScoreItem> normalized = new ArrayList<>(rubric.size());
        double total = 0;
        for (RubricItem item : rubric) {
            ScoreItem hit = submitted.stream()
                    .filter(s -> item.name().equals(s.name()))
                    .findFirst()
                    .orElseThrow(() -> new CidasBusinessException(ErrorCode.BAD_REQUEST, "缺少评分项：" + item.name()));
            if (hit.score() < 0 || hit.score() > item.maxScore()) {
                throw new CidasBusinessException(ErrorCode.BAD_REQUEST,
                        "评分项「" + item.name() + "」须在 0 ~ " + item.maxScore() + " 分之间");
            }
            normalized.add(new ScoreItem(item.name(), item.maxScore(), hit.score()));
            total += hit.score();
        }
        if (submitted.size() != normalized.size()) {
            throw new CidasBusinessException(ErrorCode.BAD_REQUEST, "评分项与作业评分标准不一致");
        }
        return new Grade(List.copyOf(normalized), total);
    }

    public record Grade(List<ScoreItem> items, double total) {
    }
}
