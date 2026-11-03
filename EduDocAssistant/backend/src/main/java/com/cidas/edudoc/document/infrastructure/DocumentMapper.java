package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {

    int insert(@Param("document") Document document);

    int updateCurrentVersion(@Param("documentId") String documentId, @Param("versionId") String versionId);

    Document selectById(@Param("documentId") String documentIdHex);

    int updateStatus(@Param("documentId") String documentIdHex, @Param("status") String status);

    List<DocumentViewRow> selectPage(@Param("ownerId") String ownerId, @Param("courseId") String courseId,
                                     @Param("docType") String docType, @Param("status") String status,
                                     @Param("keyword") String keyword, @Param("offset") int offset,
                                     @Param("limit") int limit);

    long countPage(@Param("ownerId") String ownerId, @Param("courseId") String courseId,
                   @Param("docType") String docType, @Param("status") String status,
                   @Param("keyword") String keyword);

    DocumentViewRow selectDetail(@Param("documentId") String documentIdHex);

    int softDelete(@Param("documentId") String documentIdHex);

    int restore(@Param("documentId") String documentIdHex, @Param("status") String status);
}
