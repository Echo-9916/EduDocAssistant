package com.cidas.edudoc.access.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 运维工作台聚合查询：应用层只依赖 Repository，不直连 Mapper。 */
@Repository
@RequiredArgsConstructor
public class OpsWorkbenchRepository {

    private final OpsWorkbenchMapper mapper;

    public OpsWorkbenchRow selectSummary() {
        return mapper.selectSummary();
    }
}