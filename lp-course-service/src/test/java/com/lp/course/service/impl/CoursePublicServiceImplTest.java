package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.client.LearnEntitlementClient;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.CoursePageView;
import com.lp.course.dto.PublicCourseView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseCategory;
import com.lp.course.mapper.CourseCategoryMapper;
import com.lp.course.mapper.CourseMapper;
import com.lp.course.service.CourseInternalService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the public course projection and catalog page.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class CoursePublicServiceImplTest {

    @Mock private CourseInternalService courseInternalService;

    @Mock private CourseMapper courseMapper;

    @Mock private CourseCategoryMapper courseCategoryMapper;

    @Mock private LearnEntitlementClient learnEntitlementClient;

    private CoursePublicServiceImpl coursePublicService;

    /**
     * Builds the service under test with mocked readers.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        coursePublicService =
                new CoursePublicServiceImpl(
                        courseInternalService,
                        courseMapper,
                        courseCategoryMapper,
                        learnEntitlementClient);
    }

    /**
     * A published course exposes only buyer visible fields with the price in fen.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void mapsPublishedCourse() {
        when(courseInternalService.course(7L))
                .thenReturn(
                        new CourseInternalView(
                                7L, "Course 7", "cover-7", 9900L, 2, 55L, 4L, "Teacher A"));

        PublicCourseView view = coursePublicService.course(7L, null);

        assertEquals(7L, view.getCourseId());
        assertEquals("Course 7", view.getTitle());
        assertEquals("cover-7", view.getCoverUrl());
        assertEquals(9900L, view.getPrice());
        assertEquals(2, view.getStatus());
        assertEquals(4L, view.getLessonCount());
        assertEquals("Teacher A", view.getInstructorName());
        assertFalse(view.isOwned());
        verifyNoInteractions(learnEntitlementClient);
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
                .thenReturn(
                        new CourseInternalView(
                                8L, "Course 8", "cover-8", 19900L, 3, 56L, 6L, "Teacher B"));

        PublicCourseView view = coursePublicService.course(8L, null);

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
                assertThrows(BizException.class, () -> coursePublicService.course(99L, null));

        assertEquals(40401, exception.getCode());
    }

    /**
     * A signed-in caller sees the owned flag filled from the learn-service.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void marksOwnedCoursesForSignedInCaller() {
        when(courseMapper.countPublished(null, null)).thenReturn(2L);
        when(courseMapper.selectPublishedPage(null, null, 0L, 20L))
                .thenReturn(List.of(course(1L, "Owned"), course(2L, "New")));
        when(learnEntitlementClient.ownedCourseIds(anyLong(), anyList())).thenReturn(Set.of(1L));

        CoursePageView page = coursePublicService.list(null, null, 1, 20, 1001L);

        assertEquals(2, page.getItems().size());
        assertTrue(page.getItems().get(0).isOwned());
        assertFalse(page.getItems().get(1).isOwned());
        assertEquals(2L, page.getTotal());
    }

    /**
     * An anonymous caller must not trigger an ownership lookup.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void anonymousListSkipsOwnershipLookup() {
        when(courseMapper.countPublished(null, null)).thenReturn(1L);
        when(courseMapper.selectPublishedPage(null, null, 0L, 20L))
                .thenReturn(List.of(course(1L, "Course")));

        CoursePageView page = coursePublicService.list(null, null, 1, 20, null);

        assertEquals(1, page.getItems().size());
        assertFalse(page.getItems().get(0).isOwned());
        verify(learnEntitlementClient, never()).ownedCourseIds(anyLong(), anyList());
    }

    /**
     * The slug filter resolves to a category id; an unknown slug yields an empty page.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void resolvesCategorySlugAndRejectsUnknown() {
        CourseCategory category = new CourseCategory();
        category.setId(3L);
        category.setName("Design");
        category.setSlug("design");
        when(courseCategoryMapper.selectVisibleBySlug("design")).thenReturn(category);
        when(courseCategoryMapper.selectVisibleBySlug("missing")).thenReturn(null);
        when(courseMapper.countPublished(eq(3L), any())).thenReturn(1L);
        when(courseMapper.selectPublishedPage(eq(3L), any(), eq(0L), eq(10L)))
                .thenReturn(List.of(course(1L, "Course")));

        CoursePageView page = coursePublicService.list("design", "图", 1, 10, null);
        CoursePageView missing = coursePublicService.list("missing", null, 1, 10, null);

        assertEquals(1, page.getItems().size());
        assertEquals(0L, missing.getTotal());
    }

    /**
     * Builds a catalog row with a fen price of 99.
     *
     * @param id course id
     * @param title title
     * @return row
     * @author Caspian
     * @since 0.1.0
     */
    private Course course(long id, String title) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setCoverUrl("cover-" + id);
        course.setPrice(BigDecimal.valueOf(99));
        course.setStatus(2);
        course.setInstructorName("Teacher");
        course.setLessonCount(3L);
        return course;
    }
}
