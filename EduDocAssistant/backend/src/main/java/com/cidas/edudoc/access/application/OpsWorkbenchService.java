package com.cidas.edudoc.access.application;

/** 运维工作台计数摘要（只读聚合，不建表、不新增权限点）。 */
public interface OpsWorkbenchService {

    Summary summary();

    /** 冻结人数取账号状态 L；AI 成功率按今日创建的任务计，无任务时为 0。 */
    record Summary(long userTotal, long userLocked, long documentTotal,
                   long aiTasksToday, double aiSuccessRate,
                   long storageBytes, long storageObjects, long auditHighRisk7d) {
    }
}