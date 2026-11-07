package com.cidas.edudoc.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FormatRuleMapper {

    int insertBatch(@Param("rules") List<FormatRule> rules);

    int deleteByVersion(@Param("versionId") String versionIdHex);

    List<FormatRule> selectByVersion(@Param("versionId") String versionIdHex);

    List<FormatRule> selectByIds(@Param("ids") List<String> ruleIdsHex);
}
