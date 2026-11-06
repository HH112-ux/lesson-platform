package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.PublicCourseView;
import com.lp.course.service.CourseInternalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the public course projection.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class CoursePublicServiceImplTest {

    @Mock private CourseInternalService courseInternalService;

    @InjectMocks private CoursePublicServiceImpl coursePublicService;

    /**
     * A published course exposes only buyer visible fields with the price in fen.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void mapsPublishedCourse() {
        when(courseInternalService.course(7L))
                .thenReturn(new CourseInternalView(7L, "Course 7", "cover-7", 9900L, 2, 55L, 4L));

        PublicCourseView view = coursePublicService.course(7L);

        assertEquals(7L, view.getCourseId());
        assertEquals("Course 7", view.getTitle());
        assertEquals("cover-7", view.getCoverUrl());
        assertEquals(9900L, view.getPrice());
        assertEquals(2, view.getStatus());
        assertEquals(4L, view.getLessonCount());
    }

    /**
     * An offline course is returned with {@code status=3} instead of a not-found error.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void keepsOfflineStatusVisible() {
        when(courseInternalService.course(8L))
                .thenReturn(new CourseInternalView(8L, "Course 8", "cover-8", 19900L, 3, 56L, 6L));

        PublicCourseView view = coursePublicService.course(8L);

        assertEquals(3, view.getStatus());
    }

    /**
     * A missing course keeps the documented {@code 40401} propagation.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void propagatesNotFound() {
        when(courseInternalService.course(99L)).thenThrow(new BizException(40401, "not found"));

        BizException exception =
                assertThrows(BizException.class, () -> coursePublicService.course(99L));

        assertEquals(40401, exception.getCode());
    }
}
