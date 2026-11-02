package com.cidas.edudoc.common.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxEventMapper {

    int insert(@Param("event") OutboxEvent event);
}
