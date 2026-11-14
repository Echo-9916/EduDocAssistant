package com.cidas.edudoc.access.infrastructure;

import lombok.Data;

/** 运维工作台只读聚合行（一张表一个标量，见 OpsWorkbenchMapper.xml）。 */
@Data
public class OpsWorkbenchRow {

    private long userTotal;
    private long userLocked;
    private long documentTotal;
    private long aiTasksToday;
    private long aiSucceededToday;
    private long storageBytes;
    private long storageObjects;
    private long auditHighRisk7d;
}