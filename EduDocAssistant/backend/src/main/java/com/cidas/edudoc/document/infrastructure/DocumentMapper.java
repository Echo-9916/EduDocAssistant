package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {

    int insert(@Param("document") Document document);

    int updateCurrentVersion(@Param("documentId") String documentId, @Param("versionId") String versionId);

    int updateTemplateVersion(@Param("documentId") String documentId,
                              @Param("templateVersionId") String templateVersionId);

    /** 条件前移（终审 I1）：仅当 current_version_id 仍是期望版本才成功，并发双写返回 0。 */
    int updateCurrentVersionCas(@Param("documentId") String documentId,
                                @Param("expectedVersionId") String expectedVersionId,
                                @Param("newVersionId") String newVersionId);

    Document selectById(@Param("documentId") String documentIdHex);

    /** 行锁（1106 审查 P3-1）：校验任务「查活跃+插入」去重的原子性锚点，事务内 FOR UPDATE。 */
    String selectForUpdate(@Param("documentId") String documentIdHex);

    int updateStatus(@Param("documentId") String documentIdHex, @Param("status") String status);

    /** 条件更新（1103 审查 P5）：仅当文档处于 PARSING 才迁移状态，迟到事件不得覆盖新状态。 */
    int updateStatusFromParsing(@Param("documentId") String documentIdHex, @Param("status") String status);

    List<DocumentViewRow> selectPage(@Param("ownerId") String ownerId, @Param("courseId") String courseId,
                                     @Param("docType") String docType, @Param("status") String status,
                                     @Param("keyword") String keyword, @Param("includeCollab") boolean includeCollab,
                                     @Param("offset") long offset, @Param("limit") int limit);

    long countPage(@Param("ownerId") String ownerId, @Param("courseId") String courseId,
                   @Param("docType") String docType, @Param("status") String status,
                   @Param("keyword") String keyword, @Param("includeCollab") boolean includeCollab);

    DocumentViewRow selectDetail(@Param("documentId") String documentIdHex);

    int softDelete(@Param("documentId") String documentIdHex);

    int restore(@Param("documentId") String documentIdHex, @Param("status") String status);
}
