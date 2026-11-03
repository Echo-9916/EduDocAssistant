package com.cidas.edudoc.common.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboxEventMapper {

    int insert(@Param("event") OutboxEvent event);

    List<OutboxEvent> selectPublishable(@Param("batchSize") int batchSize);

    int markPublished(@Param("id") String id);

    int markRetry(@Param("id") String id, @Param("delaySeconds") long delaySeconds);

    int markFailed(@Param("id") String id);
}
