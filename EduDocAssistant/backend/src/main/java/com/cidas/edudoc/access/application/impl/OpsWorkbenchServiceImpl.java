package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.OpsWorkbenchService;
import com.cidas.edudoc.access.infrastructure.OpsWorkbenchRepository;
import com.cidas.edudoc.access.infrastructure.OpsWorkbenchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 运维工作台摘要：SQL 在 OpsWorkbenchMapper.xml，这里只算成功率。 */
@Service
@RequiredArgsConstructor
public class OpsWorkbenchServiceImpl implements OpsWorkbenchService {

    private final OpsWorkbenchRepository repository;

    @Override
    public Summary summary() {
        OpsWorkbenchRow row = repository.selectSummary();
        return new Summary(row.getUserTotal(), row.getUserLocked(), row.getDocumentTotal(),
                row.getAiTasksToday(), successRate(row), row.getStorageBytes(), row.getStorageObjects(),
                row.getAuditHighRisk7d());
    }

    /** 成功率保留一位小数；今日无任务按 0 处理，避免除零。 */
    private static double successRate(OpsWorkbenchRow row) {
        if (row.getAiTasksToday() == 0) {
            return 0.0;
        }
        return Math.round(row.getAiSucceededToday() * 1000.0 / row.getAiTasksToday()) / 10.0;
    }
}