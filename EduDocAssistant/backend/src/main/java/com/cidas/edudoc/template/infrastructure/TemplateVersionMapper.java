package com.cidas.edudoc.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TemplateVersionMapper {

    int insert(@Param("version") TemplateVersion version);

    TemplateVersion selectById(@Param("versionId") String versionIdHex);

    Integer selectMaxVersionNo(@Param("templateId") String templateIdHex);

    List<TemplateVersion> selectListByTemplate(@Param("templateId") String templateIdHex);

    int updateValidation(@Param("versionId") String versionIdHex, @Param("validationStatus") String status);

    int markPublished(@Param("versionId") String versionIdHex, @Param("publishedBy") String publishedByHex);
}
