package com.cidas.edudoc.access.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 课程成员视角查询（SQL 委托给 {@link CourseMapper}）。 */
@Repository
@RequiredArgsConstructor
public class CourseRepository {

    private final CourseMapper mapper;

    public List<Course> listByUser(String userIdHex) {
        return mapper.selectCoursesByUser(userIdHex);
    }

    /** 有效成员课程 id 集合，用于填充 UserPrincipal.courseScope。 */
    public Set<String> courseIdsOf(String userIdHex) {
        return mapper.selectCoursesByUser(userIdHex).stream()
                .map(Course::getId)
                .collect(Collectors.toSet());
    }
}
