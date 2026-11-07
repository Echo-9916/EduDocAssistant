package com.cidas.edudoc.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TemplateMapper {

    int insert(@Param("template") Template template);

    Template selectById(@Param("templateId") String templateIdHex);

    TemplateViewRow selectView(@Param("templateId") String templateIdHex);

    List<TemplateViewRow> selectPage(@Param("role") String role, @Param("ownerId") String ownerId,
                                     @Param("courseScope") List<String> courseScope,
                                     @Param("courseId") String courseId, @Param("status") String status,
                                     @Param("unrestricted") boolean unrestricted,
                                     @Param("offset") long offset, @Param("limit") int limit);

    long countPage(@Param("role") String role, @Param("ownerId") String ownerId,
                   @Param("courseScope") List<String> courseScope,
                   @Param("courseId") String courseId, @Param("status") String status,
                   @Param("unrestricted") boolean unrestricted);

    int updateStatus(@Param("templateId") String templateIdHex, @Param("status") String status);

    int updateCurrentVersion(@Param("templateId") String templateIdHex, @Param("versionId") String versionIdHex);

    /** 草稿删除守卫：status='D' 且未发布且无任何版本行才删除（D-01/R9 口径）。 */
    int deleteDraft(@Param("templateId") String templateIdHex);
}
