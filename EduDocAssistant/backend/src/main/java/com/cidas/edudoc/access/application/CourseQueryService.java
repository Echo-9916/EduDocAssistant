package com.cidas.edudoc.access.application;

import java.util.List;

/** 当前登录用户的课程列表查询（上传表单与文档筛选的数据源）。 */
public interface CourseQueryService {

    List<CourseView> myCourses();

    record CourseView(String id, String courseCode, String courseName, String termCode, String memberRole) {
    }
}
