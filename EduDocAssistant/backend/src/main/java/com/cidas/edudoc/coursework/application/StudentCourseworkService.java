package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.common.api.PageResult;

import java.math.BigDecimal;
import java.util.List;

/** 学生侧作业查询与提交（06 切片 1，Task 1-4 / 1-5 学生视角）。 */
public interface StudentCourseworkService {

    PageResult<StudentAssignmentView> listAssignments(ListQuery query);

    SubmissionResult submit(String assignmentId, String documentId);

    /** 作业附件列表（仅本课程学生可见）。 */
    List<AttachmentView> listAttachments(String assignmentId);

    /** 下载附件字节（课程成员校验后读取私有 OSS）。 */
    AttachmentFile downloadAttachment(String assignmentId, String attachmentId);

    /** 基于附件新建一份学生自己的可编辑文档（复制附件字节并绑定作业模板）。 */
    CreatedDocumentView createDocumentFromAttachment(String assignmentId, String attachmentId);

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
                                 String submissionFormat, String requireFormatCheck,
                                 String templateVersionId, String templateName, Integer templateVersionNo) {
    }

    record SubmissionResult(String submissionId, long attemptNo, String status) {
    }

    record AttachmentView(String id, String fileName, String mimeType, long sizeBytes) {
    }

    record AttachmentFile(String fileName, String mimeType, byte[] bytes) {
    }

    record CreatedDocumentView(String documentId, String versionId) {
    }
}
