package com.lp.learn.service.impl;

import com.lp.learn.dto.TeacherLearningMetricsView;
import com.lp.learn.mapper.TeacherLearningAnalyticsMapper;
import com.lp.learn.mapper.row.TeacherAnalyticsRows;
import com.lp.learn.service.LearningAnalyticsService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Default {@link LearningAnalyticsService} implementation.
 *
 * <p>The window is a natural day range ending today. The daily series is filled with zeros so the
 * front end never has to guess missing days. Rows are grouped in memory: the per-course payload is
 * small, and one aggregation query per metric family keeps the SQL readable.
 *
 * @author Caspian
 * @since 0.16.0
 */
@Service
public class LearningAnalyticsServiceImpl implements LearningAnalyticsService {

    private static final int DEFAULT_DAYS = 30;

    private static final int MIN_DAYS = 1;

    private static final int MAX_DAYS = 365;

    private final TeacherLearningAnalyticsMapper mapper;

    /**
     * Creates the service with its mapper.
     *
     * @param mapper aggregation queries
     * @author Caspian
     * @since 0.16.0
     */
    public LearningAnalyticsServiceImpl(TeacherLearningAnalyticsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Aggregates learning behaviour of the given courses.
     *
     * @param courseIds owned course ids
     * @param days natural day window
     * @return raw counters
     * @author Caspian
     * @since 0.16.0
     */
    @Override
    public TeacherLearningMetricsView teacherMetrics(List<Long> courseIds, int days) {
        int safeDays = days <= 0 ? DEFAULT_DAYS : Math.min(Math.max(days, MIN_DAYS), MAX_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(safeDays - 1L);
        if (courseIds == null || courseIds.isEmpty()) {
            return new TeacherLearningMetricsView(
                    new TeacherLearningMetricsView.Overview(
                            0, 0, 0, 0, 0, 0, 0, null, zeroSeries(startDate, today)),
                    List.of());
        }

        LocalDateTime startAt = startDate.atStartOfDay();
        LocalDateTime endAt = today.plusDays(1).atStartOfDay();
        TeacherAnalyticsRows.OverviewRow overview =
                mapper.selectOverview(courseIds, startAt, endAt);
        if (overview == null) {
            overview = new TeacherAnalyticsRows.OverviewRow();
        }

        Map<Long, List<TeacherAnalyticsRows.HistogramRow>> histogram = new HashMap<>();
        for (TeacherAnalyticsRows.HistogramRow row : mapper.selectCompletedHistogram(courseIds)) {
            histogram.computeIfAbsent(row.courseId, key -> new ArrayList<>()).add(row);
        }
        Map<Long, List<TeacherAnalyticsRows.LessonMetricRow>> lessons = new HashMap<>();
        for (TeacherAnalyticsRows.LessonMetricRow row : mapper.selectLessonMetrics(courseIds)) {
            lessons.computeIfAbsent(row.courseId, key -> new ArrayList<>()).add(row);
        }

        List<TeacherLearningMetricsView.Course> courses = new ArrayList<>();
        for (TeacherAnalyticsRows.CourseMetricRow row :
                mapper.selectCourseMetrics(courseIds, startAt, endAt)) {
            courses.add(
                    new TeacherLearningMetricsView.Course(
                            row.courseId,
                            row.studentCount,
                            row.learningStudentCount,
                            row.activeStudentCount,
                            row.newStudentCount,
                            row.revokedStudentCount,
                            row.cumulativeWatchedSeconds,
                            row.completedLessons,
                            row.lastLearnedAt,
                            toHistogram(histogram.get(row.courseId)),
                            toLessons(lessons.get(row.courseId))));
        }
        courses.sort(Comparator.comparingLong(TeacherLearningMetricsView.Course::courseId));

        Map<LocalDate, Long> daily = new HashMap<>();
        for (TeacherAnalyticsRows.DailyActiveRow row :
                mapper.selectDailyActive(courseIds, startAt, endAt)) {
            daily.put(row.statDate, row.studentCount);
        }
        List<TeacherLearningMetricsView.DailyPoint> series = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            series.add(
                    new TeacherLearningMetricsView.DailyPoint(date, daily.getOrDefault(date, 0L)));
        }

        TeacherLearningMetricsView.Overview view =
                new TeacherLearningMetricsView.Overview(
                        overview.studentCount,
                        overview.learningStudentCount,
                        overview.activeStudentCount,
                        overview.newStudentCount,
                        overview.revokedStudentCount,
                        overview.cumulativeWatchedSeconds,
                        overview.completedLessons,
                        overview.lastLearnedAt,
                        series);
        return new TeacherLearningMetricsView(view, courses);
    }

    private List<TeacherLearningMetricsView.HistogramEntry> toHistogram(
            List<TeacherAnalyticsRows.HistogramRow> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .sorted(Comparator.comparingInt(row -> row.completedCount))
                .map(
                        row ->
                                new TeacherLearningMetricsView.HistogramEntry(
                                        row.completedCount, row.studentCount))
                .toList();
    }

    private List<TeacherLearningMetricsView.Lesson> toLessons(
            List<TeacherAnalyticsRows.LessonMetricRow> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .sorted(Comparator.comparingLong(row -> row.lessonId))
                .map(
                        row ->
                                new TeacherLearningMetricsView.Lesson(
                                        row.lessonId, row.learners, row.completedStudents))
                .toList();
    }

    private List<TeacherLearningMetricsView.DailyPoint> zeroSeries(
            LocalDate startDate, LocalDate today) {
        List<TeacherLearningMetricsView.DailyPoint> series = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            series.add(new TeacherLearningMetricsView.DailyPoint(date, 0L));
        }
        return series;
    }
}
