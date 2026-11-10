package com.cidas.edudoc.access.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 审计日志写入（SQL 见 resources/mapper/AuditLogMapper.xml）。 */
@Mapper
public interface AuditLogMapper {

    int insert(@Param("log") AuditLog log);

    // ===== 06 切片 1：审计事件检索（只读追加）=====

    /** 检索审计事件页（时间窗下界 since 为闭区间；failCount 为同操作人窗口内 FAIL 条数）。 */
    List<Map<String, Object>> searchEvents(@Param("since") OffsetDateTime since,
                                           @Param("resourceType") String resourceType,
                                           @Param("actor") String actor,
                                           @Param("keyword") String keyword,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    long countEvents(@Param("since") OffsetDateTime since,
                     @Param("resourceType") String resourceType,
                     @Param("actor") String actor,
                     @Param("keyword") String keyword);

    /** 事件详情主行（含关联文档标题与详情口径 failCount），不存在返回 null。 */
    Map<String, Object> selectEventDetail(@Param("eventId") String eventId);

    /** 同 trace_id 的全部审计行（occurred_at 升序），供详情链路聚合。 */
    List<Map<String, Object>> selectChainByTraceId(@Param("traceId") String traceId);
}
