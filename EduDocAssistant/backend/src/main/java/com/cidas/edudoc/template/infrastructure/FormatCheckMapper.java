package com.cidas.edudoc.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FormatCheckMapper {

    int insert(@Param("check") FormatCheck check);

    int insertError(@Param("check") FormatCheck check);

    FormatCheck selectById(@Param("checkId") String checkIdHex);

    FormatCheck selectActive(@Param("documentVersionId") String documentVersionIdHex,
                             @Param("templateVersionId") String templateVersionIdHex,
                             @Param("requestedBy") String requestedByHex);

    int markRunning(@Param("checkId") String checkIdHex);

    int updateResult(@Param("checkId") String checkIdHex, @Param("totalIssueCount") int totalIssueCount,
                     @Param("blockingIssueCount") int blockingIssueCount, @Param("status") String status,
                     @Param("resultSummary") String resultSummary);

    int updateError(@Param("checkId") String checkIdHex, @Param("resultSummary") String resultSummary);

    String selectDocumentTemplateBinding(@Param("documentId") String documentIdHex);

    /** 文档 owner（格式异常消息收件人，2-5 生产者③）。 */
    String selectDocumentOwner(@Param("documentId") String documentIdHex);

    String selectCoursePublishedTemplate(@Param("courseId") String courseIdHex);
}
