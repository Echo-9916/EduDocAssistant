package com.cidas.edudoc.access.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 审计日志写入（SQL 见 resources/mapper/AuditLogMapper.xml）。 */
@Mapper
public interface AuditLogMapper {

    int insert(@Param("log") AuditLog log);
}
