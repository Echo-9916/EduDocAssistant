package com.cidas.edudoc.coursework.application.impl;

import com.cidas.edudoc.common.api.PageResult;
import com.cidas.edudoc.coursework.application.StudentCourseworkService;
import com.cidas.edudoc.coursework.application.StudentCourseworkService.StudentAssignmentView;
import com.cidas.edudoc.coursework.application.StudentWorkbenchService;
import com.cidas.edudoc.document.application.CollaborationService;
import com.cidas.edudoc.document.application.DocumentQueryService;
import com.cidas.edudoc.message.application.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** 学生工作台聚合：复用各域只读服务，不在本类写 SQL。 */
@Service
@RequiredArgsConstructor
public class StudentWorkbenchServiceImpl implements StudentWorkbenchService {

    private static final int TODO_LIMIT = 5;
    private static final int RECENT_LIMIT = 5;
    // ponytail: 单学生作业按课程×学期计只有几十条；破千再把待提交数下沉成 SQL 计数
    private static final int ASSIGNMENT_SCAN_LIMIT = 100;
    private static final Set<String> PENDING_STATUSES = Set.of("PENDING", "OVERDUE");

    private final StudentCourseworkService studentCourseworkService;
    private final DocumentQueryService documentQueryService;
    private final InboxService inboxService;
    private final CollaborationService collaborationService;

    @Override
    public View summary() {
        PageResult<DocumentQueryService.Summary> docs = documentQueryService.list(
                new DocumentQueryService.ListQuery(null, null, null, null, 1, RECENT_LIMIT));
        List<StudentAssignmentView> pending = studentCourseworkService.listAssignments(
                        new StudentCourseworkService.ListQuery(null, null, 1, ASSIGNMENT_SCAN_LIMIT)).items().stream()
                .filter(view -> PENDING_STATUSES.contains(view.status()))
                .sorted(Comparator.comparing(StudentAssignmentView::deadline,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Todo> todos = pending.stream().limit(TODO_LIMIT)
                .map(view -> new Todo(view.id(), view.course(), view.title(), view.deadline(),
                        view.remainText(), view.status()))
                .toList();
        List<RecentDocument> recent = docs.items().stream()
                .map(doc -> new RecentDocument(doc.id(), doc.title(), doc.status(), doc.updateTime()))
                .toList();
        return new View(docs.total(), pending.size(), inboxService.unreadCount(),
                collaborationService.countActiveForUser(), todos, recent);
    }
}