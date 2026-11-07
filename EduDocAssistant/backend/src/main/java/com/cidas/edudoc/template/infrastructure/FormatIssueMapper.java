package com.cidas.edudoc.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FormatIssueMapper {

    int insertBatch(@Param("issues") List<FormatIssue> issues);

    List<FormatIssue> selectByCheck(@Param("checkId") String checkIdHex, @Param("severity") String severity,
                                    @Param("offset") long offset, @Param("limit") int limit);

    long countByCheck(@Param("checkId") String checkIdHex, @Param("severity") String severity);

    List<FormatIssue> selectByIds(@Param("ids") List<String> issueIdsHex);

    int updateFixStatusByIds(@Param("ids") List<String> issueIdsHex, @Param("fixStatus") String fixStatus);
}
