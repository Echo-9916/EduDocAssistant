package com.cidas.edudoc.access.application;

import com.cidas.edudoc.access.application.impl.OpsWorkbenchServiceImpl;
import com.cidas.edudoc.access.infrastructure.OpsWorkbenchRepository;
import com.cidas.edudoc.access.infrastructure.OpsWorkbenchRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 工作台摘要：字段透传 + 成功率一位小数、无任务不除零。 */
class OpsWorkbenchServiceTest {

    private final OpsWorkbenchRepository repository = mock(OpsWorkbenchRepository.class);
    private final OpsWorkbenchService service = new OpsWorkbenchServiceImpl(repository);

    @Test
    void mapsRowAndRoundsSuccessRate() {
        OpsWorkbenchRow row = new OpsWorkbenchRow();
        row.setUserTotal(10);
        row.setUserLocked(1);
        row.setDocumentTotal(23);
        row.setAiTasksToday(3);
        row.setAiSucceededToday(2);
        row.setStorageBytes(1024);
        row.setStorageObjects(4);
        row.setAuditHighRisk7d(5);
        when(repository.selectSummary()).thenReturn(row);

        OpsWorkbenchService.Summary summary = service.summary();

        assertEquals(10, summary.userTotal());
        assertEquals(1, summary.userLocked());
        assertEquals(23, summary.documentTotal());
        assertEquals(3, summary.aiTasksToday());
        assertEquals(66.7, summary.aiSuccessRate());
        assertEquals(1024, summary.storageBytes());
        assertEquals(4, summary.storageObjects());
        assertEquals(5, summary.auditHighRisk7d());
    }

    @Test
    void noTasksTodayMeansZeroRate() {
        OpsWorkbenchRow row = new OpsWorkbenchRow();
        when(repository.selectSummary()).thenReturn(row);

        assertEquals(0.0, service.summary().aiSuccessRate());
    }
}