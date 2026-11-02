package com.cidas.edudoc.access.application.impl;

import com.cidas.edudoc.access.application.CourseQueryService.CourseView;
import com.cidas.edudoc.access.infrastructure.Course;
import com.cidas.edudoc.access.infrastructure.CourseRepository;
import com.cidas.edudoc.common.security.AuthContext;
import com.cidas.edudoc.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseQueryServiceImplTest {

    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CourseQueryServiceImpl service = new CourseQueryServiceImpl(courseRepository);

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void mapsRepositoryRowsToViewsForCurrentUser() {
        AuthContext.set(new UserPrincipal("U1", "teacher", null, Set.of("TEACHER"), Set.of(), Set.of("C1")));
        when(courseRepository.listByUser("U1")).thenReturn(
                List.of(new Course("C1", "CS101", "计算机导论", "2026-FALL", "TEACHER")));

        List<CourseView> views = service.myCourses();

        assertEquals(1, views.size());
        assertEquals("CS101", views.get(0).courseCode());
        assertEquals("TEACHER", views.get(0).memberRole());
    }
}
