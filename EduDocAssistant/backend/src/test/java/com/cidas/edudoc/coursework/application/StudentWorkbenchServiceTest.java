package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.StudentAssignmentView;
import com.cidas.edudoc.coursework.application.impl.StudentWorkbenchServiceImpl;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.application.DocumentQueryService;
import com.cidas.edudoc.message.application.InboxService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 学生工作台聚合：待办只取 PENDING/OVERDUE、按截止时间升序、最多 5 条。 */
class StudentWorkbenchServiceTest {

    private final StudentCourseworkService coursework = mock(StudentCourseworkService.class);
    private final DocumentQueryService documents = mock(DocumentQueryService.class);
    private final InboxService inbox = mock(InboxService.class);
    private final CollaborationService collaboration = mock(CollaborationService.class);
    private final StudentWorkbenchService service =
            new StudentWorkbenchServiceImpl(coursework, documents, inbox, collaboration);

    @Test
    void aggregatesCountsAndPendingTodosInDeadlineOrder() {
        when(coursework.listAssignments(any())).thenReturn(new PageResult<>(List.of(
                assignment("A1", "晚交作业", "Web开发技术", "2026-11-20T18:00:00+08:00", "PENDING"),
                assignment("A3", "已批改作业", "数据库原理", "2026-11-10T18:00:00+08:00", "GRADED"),
                assignment("A2", "已逾期作业", "数据库原理", "2026-11-16T18:00:00+08:00", "OVERDUE")),
                3, 1, 100));
        when(documents.list(any())).thenReturn(new PageResult<>(List.of(
                new DocumentQueryService.Summary("D1", "设计报告", "REPORT", "READY", null, null,
                        null, null, 1, "2026-11-15T00:00:00Z", "2026-11-15T01:00:00Z", "张同学", null)),
                7, 1, 5));
        when(inbox.unreadCount()).thenReturn(3L);
        when(collaboration.countActiveForUser()).thenReturn(1L);

        StudentWorkbenchService.View view = service.summary();

        assertEquals(7, view.documentTotal());
        assertEquals(2, view.pendingAssignmentTotal());
        assertEquals(3, view.unreadMessageTotal());
        assertEquals(1, view.activeCollaborationTotal());
        assertEquals(List.of("A2", "A1"), view.todos().stream()
                .map(StudentWorkbenchService.Todo::assignmentId).toList());
        assertEquals("设计报告", view.recentDocuments().get(0).title());
    }

    private static StudentAssignmentView assignment(String id, String title, String course,
                                                    String deadline, String status) {
        return new StudentAssignmentView(id, course, title, null, deadline, "剩余 1 天", status,
                new StudentCourseworkService.FormatView("NONE", "未做格式校验"),
                null, null, null, null, null, null, null, null, "BOTH", "N",
                null, null, null);
    }
}