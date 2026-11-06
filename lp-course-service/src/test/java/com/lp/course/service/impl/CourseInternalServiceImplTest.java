package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.dto.CourseInternalView;
import com.lp.course.dto.CourseReviewResultView;
import com.lp.course.dto.LessonInternalView;
import com.lp.course.dto.PendingCoursePageView;
import com.lp.course.dto.PendingCourseView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.CourseMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for internal course queries; the mapper is mocked so no database is needed.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class CourseInternalServiceImplTest {

    @Mock private CourseMapper courseMapper;

    @InjectMocks private CourseInternalServiceImpl courseInternalService;

    /**
     * An existing published course is projected with its price converted to fen.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void returnsCourseWithPriceInFen() {
        Course course = course(2, "99.00", 7L, 3L);
        when(courseMapper.selectById(7L)).thenReturn(course);

        CourseInternalView view = courseInternalService.course(7L);

        assertEquals(7L, view.getCourseId());
        assertEquals("Course 7", view.getTitle());
        assertEquals("cover-7", view.getCoverUrl());
        assertEquals(9900L, view.getPrice());
        assertEquals(2, view.getStatus());
        assertEquals(3L, view.getTeacherId());
        assertEquals(4L, view.getLessonCount());
    }

    /**
     * A missing course fails with the documented {@code 40401} code.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMissingCourseWithNotFound() {
        when(courseMapper.selectById(99L)).thenReturn(null);

        BizException exception =
                assertThrows(BizException.class, () -> courseInternalService.course(99L));

        assertEquals(40401, exception.getCode());
    }

    /**
     * An offline course stays readable so the caller can reject it with a trade error.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void returnsOfflineCourseWithStatus() {
        Course course = course(3, "199.00", 8L, 5L);
        when(courseMapper.selectById(8L)).thenReturn(course);

        CourseInternalView view = courseInternalService.course(8L);

        assertEquals(3, view.getStatus());
        assertEquals(19900L, view.getPrice());
        assertEquals(5L, view.getTeacherId());
    }

    /**
     * Lessons of an existing course keep the trial flag and chapter grouping.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void mapsLessonOutline() {
        when(courseMapper.selectById(8L)).thenReturn(course(2, "10.00", 8L, 3L));
        when(courseMapper.selectLessons(8L))
                .thenReturn(List.of(lesson(101L, 8L, 11L, 1), lesson(102L, 8L, 11L, 0)));

        List<LessonInternalView> lessons = courseInternalService.lessons(8L);

        assertEquals(2, lessons.size());
        assertTrue(lessons.get(0).isFree());
        assertFalse(lessons.get(1).isFree());
        assertEquals(11L, lessons.get(0).getChapterId());
    }

    /**
     * Lessons of a missing course fail with {@code 40401} instead of an empty list.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsLessonsOfMissingCourse() {
        when(courseMapper.selectById(99L)).thenReturn(null);

        BizException exception =
                assertThrows(BizException.class, () -> courseInternalService.lessons(99L));

        assertEquals(40401, exception.getCode());
    }

    /**
     * The pending queue clamps paging and returns the mapper rows untouched.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void listsPendingCoursesWithClampedPaging() {
        PendingCourseView row = new PendingCourseView();
        row.setCourseId(7L);
        row.setTitle("Course 7");
        when(courseMapper.countByStatus(1)).thenReturn(1L);
        when(courseMapper.selectPageByStatus(1, 0L, 100)).thenReturn(List.of(row));

        PendingCoursePageView page = courseInternalService.pendingCourses(0, 500);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getPageNum());
        assertEquals(100, page.getPageSize());
        assertEquals(7L, page.getRecords().get(0).getCourseId());
    }

    /**
     * An empty queue skips the row query.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void returnsEmptyPendingQueue() {
        when(courseMapper.countByStatus(1)).thenReturn(0L);

        PendingCoursePageView page = courseInternalService.pendingCourses(1, 20);

        assertEquals(0, page.getRecords().size());
        verify(courseMapper, never()).selectPageByStatus(anyInt(), anyLong(), anyLong());
    }

    /**
     * Approval publishes the course and reports the applied price.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void approvesPendingCourse() {
        when(courseMapper.approve(eq(7L), any(BigDecimal.class), eq(55L), any(LocalDateTime.class)))
                .thenReturn(1);

        CourseReviewResultView result = courseInternalService.review(7L, true, 9900L, null, 55L);

        assertEquals(2, result.getStatus());
        assertEquals(9900L, result.getPriceFen());
        verify(courseMapper)
                .approve(
                        eq(7L),
                        argThat(
                                price ->
                                        price != null
                                                && price.compareTo(new BigDecimal("99.00")) == 0),
                        eq(55L),
                        any(LocalDateTime.class));
    }

    /**
     * Rejection requires a remark and moves the course back to draft.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsWithRemarkAndReadsCurrentPrice() {
        when(courseMapper.reject(eq(7L), eq("missing outline"), eq(55L), any(LocalDateTime.class)))
                .thenReturn(1);
        when(courseMapper.selectPrice(7L)).thenReturn(new BigDecimal("12.30"));

        CourseReviewResultView result =
                courseInternalService.review(7L, false, null, "  missing outline ", 55L);

        assertEquals(0, result.getStatus());
        assertEquals(1230L, result.getPriceFen());
    }

    /**
     * A rejection without a remark never touches the database.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsBlankRemark() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> courseInternalService.review(7L, false, null, " ", 55L));

        assertEquals(40001, exception.getCode());
        verify(courseMapper, never()).reject(anyLong(), any(), anyLong(), any(LocalDateTime.class));
    }

    /**
     * A course that is no longer pending reports the documented state conflict.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsReviewWhenCourseIsNotPending() {
        when(courseMapper.approve(eq(7L), any(), eq(55L), any(LocalDateTime.class))).thenReturn(0);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> courseInternalService.review(7L, true, 100L, null, 55L));

        assertEquals(61005, exception.getCode());
    }

    private Course course(int status, String price, long courseId, long teacherId) {
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("Course " + courseId);
        course.setCoverUrl("cover-" + courseId);
        course.setPrice(new BigDecimal(price));
        course.setStatus(status);
        course.setOwnerTeacherId(teacherId);
        course.setLessonCount(4L);
        return course;
    }

    private CourseLesson lesson(long id, long courseId, long chapterId, int free) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(id);
        lesson.setCourseId(courseId);
        lesson.setChapterId(chapterId);
        lesson.setTitle("Lesson " + id);
        lesson.setIsFree(free);
        lesson.setSort((int) (id - 100));
        return lesson;
    }
}
