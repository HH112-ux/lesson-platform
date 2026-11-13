package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.client.LearningAnalyticsClient;
import com.lp.course.client.LearningMetricsSnapshot;
import com.lp.course.dto.TeacherAnalyticsOverviewView;
import com.lp.course.dto.TeacherCourseAnalyticsDetailView;
import com.lp.course.dto.TeacherCourseAnalyticsPageView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.TeacherCourseMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the teacher analytics service, focused on ownership and aggregation math.
 *
 * @author Caspian
 * @since 0.16.0
 */
@ExtendWith(MockitoExtension.class)
class TeacherAnalyticsServiceImplTest {

    @Mock private TeacherCourseMapper teacherCourseMapper;

    @Mock private LearningAnalyticsClient learningAnalyticsClient;

    private TeacherAnalyticsServiceImpl service;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @BeforeEach
    void setUp() {
        service = new TeacherAnalyticsServiceImpl(teacherCourseMapper, learningAnalyticsClient);
    }

    /**
     * A teacher cannot read another teacher's course, and the learn-service is never called.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void foreignCourseIsForbiddenBeforeCallingLearn() {
        when(teacherCourseMapper.selectTeacherIdByUser(1L)).thenReturn(10L);
        Course course = course(99L, 20L, 3, "Other", 3);
        when(teacherCourseMapper.selectById(99L)).thenReturn(course);

        BizException exception =
                assertThrows(BizException.class, () -> service.course(1L, "TEACHER", 99L, 30));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(learningAnalyticsClient);
    }

    /**
     * A missing course is reported as not found before ownership is evaluated.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void missingCourseIsNotFound() {
        when(teacherCourseMapper.selectTeacherIdByUser(1L)).thenReturn(10L);
        when(teacherCourseMapper.selectById(99L)).thenReturn(null);

        BizException exception =
                assertThrows(BizException.class, () -> service.course(1L, "TEACHER", 99L, 30));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verifyNoInteractions(learningAnalyticsClient);
    }

    /**
     * A student role cannot open the teacher dashboard.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void studentRoleIsForbidden() {
        BizException exception =
                assertThrows(BizException.class, () -> service.overview(1L, "STUDENT", 30));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(teacherCourseMapper, learningAnalyticsClient);
    }

    /**
     * An administrator without a teacher profile is refused instead of seeing every course.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void adminWithoutTeacherProfileIsForbidden() {
        when(teacherCourseMapper.selectTeacherIdByUser(1L)).thenReturn(null);

        BizException exception =
                assertThrows(BizException.class, () -> service.overview(1L, "ADMIN", 30));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(learningAnalyticsClient);
    }

    /**
     * The overview aggregates only the caller's courses and uses learning students as denominator.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void overviewUsesOwnedCoursesAndLearningDenominator() {
        when(teacherCourseMapper.selectTeacherIdByUser(1L)).thenReturn(10L);
        when(teacherCourseMapper.selectAnalyticsCoursesByOwner(10L))
                .thenReturn(
                        List.of(
                                course(7L, 10L, 2, "Course A", 4),
                                course(8L, 10L, 2, "Course B", 2)));
        when(learningAnalyticsClient.teacherMetrics(List.of(7L, 8L), 30))
                .thenReturn(
                        new LearningMetricsSnapshot(
                                new LearningMetricsSnapshot.Overview(
                                        10,
                                        5,
                                        4,
                                        1,
                                        0,
                                        3600,
                                        6,
                                        null,
                                        List.of(
                                                new LearningMetricsSnapshot.DailyPoint(
                                                        LocalDate.now(), 4))),
                                List.of(
                                        new LearningMetricsSnapshot.Course(
                                                7L,
                                                6,
                                                3,
                                                2,
                                                1,
                                                0,
                                                2400,
                                                4,
                                                null,
                                                List.of(
                                                        new LearningMetricsSnapshot.HistogramEntry(
                                                                4, 1)),
                                                List.of()),
                                        new LearningMetricsSnapshot.Course(
                                                8L,
                                                4,
                                                2,
                                                2,
                                                0,
                                                0,
                                                1200,
                                                2,
                                                null,
                                                List.of(
                                                        new LearningMetricsSnapshot.HistogramEntry(
                                                                1, 1)),
                                                List.of()))));

        TeacherAnalyticsOverviewView view = service.overview(1L, "TEACHER", 30);

        verify(learningAnalyticsClient).teacherMetrics(List.of(7L, 8L), 30);
        assertEquals(2, view.courseCount());
        assertEquals(2, view.publishedCourseCount());
        assertEquals(5, view.learningCourseStudents());
        assertEquals(1, view.completedCourseStudents());
        assertEquals(20, view.completionRatePercent());
        assertEquals(1, view.dailyActiveStudents().size());
    }

    /**
     * The course table asks the learn-service only for the courses of the current page.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void coursePageQueriesOnlyVisiblePage() {
        when(teacherCourseMapper.selectTeacherIdByUser(1L)).thenReturn(10L);
        when(teacherCourseMapper.selectAnalyticsCoursesByOwner(10L))
                .thenReturn(
                        List.of(
                                course(7L, 10L, 2, "A", 2),
                                course(8L, 10L, 2, "B", 2),
                                course(9L, 10L, 2, "C", 2)));
        when(learningAnalyticsClient.teacherMetrics(List.of(7L, 8L), 7))
                .thenReturn(
                        new LearningMetricsSnapshot(
                                new LearningMetricsSnapshot.Overview(
                                        0, 0, 0, 0, 0, 0, 0, null, List.of()),
                                List.of()));

        TeacherCourseAnalyticsPageView page = service.courses(1L, "TEACHER", 7, 1, 2);

        verify(learningAnalyticsClient).teacherMetrics(List.of(7L, 8L), 7);
        assertEquals(3, page.total());
        assertEquals(2, page.records().size());
        assertNull(page.records().get(0).completionRatePercent());
    }

    /**
     * The detail distribution sums to the learning student count and keeps the fixed bucket order.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void detailBuildsDistributionAndLessonRows() {
        when(teacherCourseMapper.selectTeacherIdByUser(1L)).thenReturn(10L);
        when(teacherCourseMapper.selectById(7L)).thenReturn(course(7L, 10L, 2, "Course A", 2));
        when(teacherCourseMapper.selectLessons(7L))
                .thenReturn(List.of(lesson(11L, "One", 1), lesson(12L, "Two", 1)));
        when(learningAnalyticsClient.teacherMetrics(List.of(7L), 30))
                .thenReturn(
                        new LearningMetricsSnapshot(
                                new LearningMetricsSnapshot.Overview(
                                        0, 0, 0, 0, 0, 0, 0, null, List.of()),
                                List.of(
                                        new LearningMetricsSnapshot.Course(
                                                7L,
                                                6,
                                                4,
                                                3,
                                                0,
                                                0,
                                                900,
                                                5,
                                                null,
                                                List.of(
                                                        new LearningMetricsSnapshot.HistogramEntry(
                                                                2, 1),
                                                        new LearningMetricsSnapshot.HistogramEntry(
                                                                1, 2)),
                                                List.of(
                                                        new LearningMetricsSnapshot.Lesson(
                                                                11L, 4, 3),
                                                        new LearningMetricsSnapshot.Lesson(
                                                                12L, 4, 2))))));

        TeacherCourseAnalyticsDetailView view = service.course(1L, "TEACHER", 7L, 30);

        assertEquals(2, view.lessonCount());
        assertEquals(1, view.completedCourseStudents());
        assertEquals(25, view.completionRatePercent());
        assertEquals(
                4,
                view.progressDistribution().stream()
                        .mapToLong(TeacherCourseAnalyticsDetailView.ProgressBucket::studentCount)
                        .sum());
        assertEquals("not-started", view.progressDistribution().get(0).bucket());
        assertEquals("100", view.progressDistribution().get(5).bucket());
        assertEquals(2, view.lessons().size());
        assertEquals(75, view.lessons().get(0).completionRatePercent());
        assertEquals(50, view.lessons().get(1).completionRatePercent());
    }

    private Course course(long id, long ownerId, int status, String title, long lessonCount) {
        Course course = new Course();
        course.setId(id);
        course.setOwnerTeacherId(ownerId);
        course.setStatus(status);
        course.setTitle(title);
        course.setLessonCount(lessonCount);
        return course;
    }

    private CourseLesson lesson(long id, String title, int status) {
        CourseLesson lesson = new CourseLesson();
        lesson.setId(id);
        lesson.setTitle(title);
        lesson.setStatus(status);
        return lesson;
    }
}
