package com.lp.course.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lp.common.identity.IdentityHeaders;
import com.lp.common.web.GlobalExceptionHandler;
import com.lp.course.client.LearningAnalyticsClient;
import com.lp.course.entity.Course;
import com.lp.course.mapper.TeacherCourseMapper;
import com.lp.course.service.impl.TeacherAnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Runs the ownership negative case through the HTTP layer.
 *
 * <p>The service test already proves the exception code; this test keeps the controller, the
 * exception envelope and the dependency call count honest in one pass. The envelope carries {@code
 * 40301} while the HTTP status stays {@code 200}, as required for every endpoint of this
 * repository.
 *
 * @author Caspian
 * @since 0.16.0
 */
class TeacherAnalyticsControllerTest {

    private TeacherCourseMapper teacherCourseMapper;

    private LearningAnalyticsClient learningAnalyticsClient;

    private MockMvc mockMvc;

    /**
     * Wires the real controller and service against mocked gateway headers and dependencies.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @BeforeEach
    void setUp() {
        teacherCourseMapper = mock(TeacherCourseMapper.class);
        learningAnalyticsClient = mock(LearningAnalyticsClient.class);
        TeacherAnalyticsServiceImpl service =
                new TeacherAnalyticsServiceImpl(teacherCourseMapper, learningAnalyticsClient);
        mockMvc =
                MockMvcBuilders.standaloneSetup(new TeacherAnalyticsController(service))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    /**
     * Teacher A reading teacher B's course gets the forbidden envelope and never reaches learn.
     *
     * @throws Exception when MockMvc fails to perform the request
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void foreignCourseReturnsForbiddenEnvelopeWithoutCallingLearn() throws Exception {
        when(teacherCourseMapper.selectTeacherIdByUser(1L)).thenReturn(10L);
        Course course = new Course();
        course.setId(99L);
        course.setOwnerTeacherId(20L);
        course.setTitle("Other teacher course");
        when(teacherCourseMapper.selectById(99L)).thenReturn(course);

        mockMvc.perform(
                        get("/api/v1/teacher/analytics/courses/99")
                                .header(IdentityHeaders.USER_ID, "1")
                                .header(IdentityHeaders.USER_ROLES, "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        verifyNoInteractions(learningAnalyticsClient);
    }
}
