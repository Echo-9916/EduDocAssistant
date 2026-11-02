package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.CourseQueryService;
import com.cidas.edudoc.access.infrastructure.CourseRepository;
import com.cidas.edudoc.common.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseQueryServiceImpl implements CourseQueryService {

    private final CourseRepository courseRepository;

    @Override
    public List<CourseView> myCourses() {
        return courseRepository.listByUser(AuthContext.require().userId()).stream()
                .map(c -> new CourseView(c.getId(), c.getCourseCode(), c.getCourseName(),
                        c.getTermCode(), c.getMemberRole()))
                .toList();
    }
}
