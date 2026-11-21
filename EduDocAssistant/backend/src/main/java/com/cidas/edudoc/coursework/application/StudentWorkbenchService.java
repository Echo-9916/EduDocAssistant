package com.cidas.edudoc.coursework.application;

import java.util.List;

/**
 * 学生工作台聚合：一次返回四个统计卡 + 待办列表 + 最近文档。
 * 前端按需取舍；新增展示项优先往这里加字段，不再拆小接口。
 */
public interface StudentWorkbenchService {

    View summary();

    record View(long documentTotal, long pendingAssignmentTotal, long unreadMessageTotal,
                long activeCollaborationTotal, List<Todo> todos, List<RecentDocument> recentDocuments) {
    }

    record Todo(String assignmentId, String courseName, String title, String deadline,
                String remainText, String status) {
    }

    record RecentDocument(String id, String title, String status, String updateTime) {
    }
}