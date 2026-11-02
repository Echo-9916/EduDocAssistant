package com.cidas.edudoc.access.api;

import com.cidas.edudoc.access.application.CourseQueryService;
import com.cidas.edudoc.access.application.CourseQueryService.CourseView;
import com.cidas.edudoc.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseControllerTest {

    private final CourseQueryService courseQueryService = mock(CourseQueryService.class);
    private final CourseController controller = new CourseController(courseQueryService);

    @Test
    void returnsCourseEnvelope() {
        when(courseQueryService.myCourses()).thenReturn(
                List.of(new CourseView("C1", "CS101", "计算机导论", "2026-FALL", "STUDENT")));

        ApiResponse<List<CourseView>> response = controller.myCourses();

        assertEquals(200, response.code());
        assertEquals("CS101", response.data().get(0).courseCode());
    }
}
