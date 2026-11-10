package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.common.api.PageResult;

import java.math.BigDecimal;

/** 学生侧作业查询与提交（06 切片 1，Task 1-4 / 1-5 学生视角）。 */
public interface StudentCourseworkService {

    PageResult<StudentAssignmentView> listAssignments(ListQuery query);

    SubmissionResult submit(String assignmentId, String documentId);

    record ListQuery(String status, String courseId, Integer page, Integer pageSize) {
    }

    /** 格式校验结论视图：state 取 NONE/PASS/WARN/BLOCK，note 为一句人话说明。 */
    record FormatView(String state, String note) {
    }

    record StudentAssignmentView(String id, String course, String title, String requirement,
                                 String deadline, String remainText, String status,
                                 FormatView format, BigDecimal score, String feedback,
                                 Integer attemptNo, String submittedDoc, String submitTime,
                                 String documentId, String documentVersionId, Integer versionNo,
                                 String submissionFormat, String requireFormatCheck) {
    }

    record SubmissionResult(String submissionId, long attemptNo, String status) {
    }
}
