package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.CourseQueryService;
import com.cidas.edudoc.access.application.CourseQueryService.CourseView;
import com.cidas.edudoc.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 课程列表：认证即可（无权限点），供文档上传表单与筛选下拉。 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseQueryService courseQueryService;

    @GetMapping
    public ApiResponse<List<CourseView>> myCourses() {
        return ApiResponse.ok(courseQueryService.myCourses());
    }
}
