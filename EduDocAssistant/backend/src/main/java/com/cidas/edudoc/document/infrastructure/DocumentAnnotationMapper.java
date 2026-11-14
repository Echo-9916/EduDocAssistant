package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 文档批注读写（SQL 见 resources/mapper/DocumentAnnotationMapper.xml）。 */
@Mapper
public interface DocumentAnnotationMapper {

    int insert(@Param("idHex") String idHex, @Param("documentIdHex") String documentIdHex,
               @Param("versionIdHex") String versionIdHex, @Param("authorIdHex") String authorIdHex,
               @Param("charStart") int charStart, @Param("charEnd") int charEnd,
               @Param("content") String content);

    List<DocumentAnnotationRow> selectByDocument(@Param("documentIdHex") String documentIdHex,
                                                 @Param("status") String status);

    DocumentAnnotationRow selectById(@Param("annotationIdHex") String annotationIdHex);

    int resolve(@Param("annotationIdHex") String annotationIdHex, @Param("resolvedByHex") String resolvedByHex);

    int reopen(@Param("annotationIdHex") String annotationIdHex);

    int delete(@Param("annotationIdHex") String annotationIdHex);
}