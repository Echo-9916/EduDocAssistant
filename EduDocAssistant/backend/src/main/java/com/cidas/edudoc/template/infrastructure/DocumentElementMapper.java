package com.cidas.edudoc.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentElementMapper {

    int insertBatch(@Param("elements") List<DocumentElement> elements);

    int deleteByVersion(@Param("versionId") String versionIdHex);

    List<DocumentElement> selectByVersion(@Param("versionId") String versionIdHex);

    int countByVersion(@Param("versionId") String versionIdHex);
}
