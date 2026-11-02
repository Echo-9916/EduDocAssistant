package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentMapper {

    int insert(@Param("document") Document document);

    int updateCurrentVersion(@Param("documentId") String documentId, @Param("versionId") String versionId);
}
