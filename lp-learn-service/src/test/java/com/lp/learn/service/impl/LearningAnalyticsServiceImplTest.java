package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.learn.dto.TeacherLearningMetricsView;
import com.lp.learn.mapper.TeacherLearningAnalyticsMapper;
import com.lp.learn.mapper.row.TeacherAnalyticsRows;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the teacher learning aggregation; the mapper is mocked.
 *
 * @author Caspian
 * @since 0.16.0
 */
@ExtendWith(MockitoExtension.class)
class LearningAnalyticsServiceImplTest {

    @Mock private TeacherLearningAnalyticsMapper mapper;

    private LearningAnalyticsServiceImpl service;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @BeforeEach
    void setUp() {
        service = new LearningAnalyticsServiceImpl(mapper);
    }

    /**
     * An empty course list yields zeros and never touches the database.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void emptyCoursesReturnZeroWithoutQuery() {
        TeacherLearningMetricsView view = service.teacherMetrics(List.of(), 30);

        assertEquals(0, view.overview().studentCount());
        assertEquals(30, view.overview().dailyActiveStudents().size());
        assertTrue(view.courses().isEmpty());
        verifyNoInteractions(mapper);
    }

    /**
     * Aggregates are merged per course and missing days are filled with zero.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void mergesCourseRowsAndFillsDailySeries() {
        TeacherAnalyticsRows.OverviewRow overview = new TeacherAnalyticsRows.OverviewRow();
        overview.studentCount = 42;
        overview.learningStudentCount = 30;
        overview.activeStudentCount = 12;
        overview.newStudentCount = 5;
        overview.revokedStudentCount = 2;
        overview.cumulativeWatchedSeconds = 3600;
        overview.completedLessons = 21;
        when(mapper.selectOverview(anyList(), any(), any())).thenReturn(overview);

        TeacherAnalyticsRows.CourseMetricRow course = new TeacherAnalyticsRows.CourseMetricRow();
        course.courseId = 7L;
        course.studentCount = 42;
        course.learningStudentCount = 30;
        course.cumulativeWatchedSeconds = 3600;
        course.completedLessons = 21;
        when(mapper.selectCourseMetrics(anyList(), any(), any())).thenReturn(List.of(course));

        TeacherAnalyticsRows.HistogramRow histogram = new TeacherAnalyticsRows.HistogramRow();
        histogram.courseId = 7L;
        histogram.completedCount = 3;
        histogram.studentCount = 8;
        when(mapper.selectCompletedHistogram(anyList())).thenReturn(List.of(histogram));

        TeacherAnalyticsRows.LessonMetricRow lesson = new TeacherAnalyticsRows.LessonMetricRow();
        lesson.courseId = 7L;
        lesson.lessonId = 11L;
        lesson.learners = 20;
        lesson.completedStudents = 15;
        when(mapper.selectLessonMetrics(anyList())).thenReturn(List.of(lesson));

        TeacherAnalyticsRows.DailyActiveRow active = new TeacherAnalyticsRows.DailyActiveRow();
        active.statDate = LocalDate.now().minusDays(1);
        active.studentCount = 9;
        when(mapper.selectDailyActive(anyList(), any(), any())).thenReturn(List.of(active));

        TeacherLearningMetricsView view = service.teacherMetrics(List.of(7L), 7);

        assertEquals(42, view.overview().studentCount());
        assertEquals(7, view.overview().dailyActiveStudents().size());
        assertEquals(
                9,
                view.overview().dailyActiveStudents().stream()
                        .mapToLong(TeacherLearningMetricsView.DailyPoint::studentCount)
                        .sum());
        assertEquals(1, view.courses().size());
        TeacherLearningMetricsView.Course row = view.courses().get(0);
        assertEquals(3600, row.cumulativeWatchedSeconds());
        assertEquals(1, row.completedLessonHistogram().size());
        assertEquals(3, row.completedLessonHistogram().get(0).completedLessons());
        assertEquals(8, row.completedLessonHistogram().get(0).studentCount());
        assertEquals(1, row.lessons().size());
        assertEquals(15, row.lessons().get(0).completedStudents());
    }

    /**
     * A zero or negative day argument falls back to the default window.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void invalidDaysFallBackToDefault() {
        TeacherAnalyticsRows.OverviewRow overview = new TeacherAnalyticsRows.OverviewRow();
        when(mapper.selectOverview(anyList(), any(), any())).thenReturn(overview);
        when(mapper.selectCourseMetrics(anyList(), any(), any())).thenReturn(List.of());
        when(mapper.selectCompletedHistogram(anyList())).thenReturn(List.of());
        when(mapper.selectLessonMetrics(anyList())).thenReturn(List.of());
        when(mapper.selectDailyActive(anyList(), any(), any())).thenReturn(List.of());

        TeacherLearningMetricsView view = service.teacherMetrics(List.of(7L), 0);

        assertEquals(30, view.overview().dailyActiveStudents().size());
    }
}
