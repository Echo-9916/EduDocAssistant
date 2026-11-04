package com.cidas.edudoc.ai.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiModelProfileMapper {

    AiModelProfile selectByCode(@Param("profileCode") String profileCode);
}
