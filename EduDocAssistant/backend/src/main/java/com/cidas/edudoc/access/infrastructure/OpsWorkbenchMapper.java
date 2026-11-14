package com.cidas.edudoc.access.infrastructure;

import org.apache.ibatis.annotations.Mapper;

/** 运维工作台计数聚合（只读，SQL 见 resources/mapper/OpsWorkbenchMapper.xml）。 */
@Mapper
public interface OpsWorkbenchMapper {

    OpsWorkbenchRow selectSummary();
}