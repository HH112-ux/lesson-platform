package com.lp.course.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.client.LearningAnalyticsClient;
import com.lp.course.client.LearningMetricsSnapshot;
import com.lp.course.dto.TeacherAnalyticsOverviewView;
import com.lp.course.dto.TeacherCourseAnalyticsDetailView;
import com.lp.course.dto.TeacherCourseAnalyticsPageView;
import com.lp.course.dto.TeacherCourseAnalyticsView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import com.lp.course.mapper.TeacherCourseMapper;
import com.lp.course.service.TeacherAnalyticsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Default {@link TeacherAnalyticsService} implementation.
 *
 * <p>Ownership is resolved here, once, and the learn-service is called with an explicit course id
 * list. A course detail request is checked against {@code owner_teacher_id} before any learning
 * data is requested, so a foreign course id never reaches the dependency.
 *
 * @author Caspian
 * @since 0.16.0
 */
@Service
public class TeacherAnalyticsServiceImpl implements TeacherAnalyticsService {

    private static final int DEFAULT_DAYS = 30;

    private static final int MIN_DAYS = 1;

    private static final int MAX_DAYS = 365;

    private static final int MAX_COURSES = 200;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private static final int STATUS_PUBLISHED = 2;

    private static final int STATUS_VISIBLE_LESSON = 1;

    private static final int FULL_PERCENT = 100;

    private static final List<String> BUCKETS =
            List.of("not-started", "0-24", "25-49", "50-74", "75-99", "100");

    private final TeacherCourseMapper teacherCourseMapper;

    private final LearningAnalyticsClient learningAnalyticsClient;

    /**
     * Creates the service with its dependencies.
     *
     * @param teacherCourseMapper teacher scoped course queries
     * @param learningAnalyticsClient learn-service aggregation client
     * @author Caspian
     * @since 0.16.0
     */
    public TeacherAnalyticsServiceImpl(
            TeacherCourseMapper teacherCourseMapper,
            LearningAnalyticsClient learningAnalyticsClient) {
        this.teacherCourseMapper = teacherCourseMapper;
        this.learningAnalyticsClient = learningAnalyticsClient;
    }

    /**
     * Returns the teacher wide summary.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param days requested natural day window
     * @return summary including the daily active series
     * @author Caspian
     * @since 0.16.0
     */
    @Override
    public TeacherAnalyticsOverviewView overview(long userId, String roles, int days) {
        long teacherId = requireTeacherId(userId, roles);
        int safeDays = clampDays(days);
        List<Course> courses = teacherCourseMapper.selectAnalyticsCoursesByOwner(teacherId);
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        if (courseIds.size() > MAX_COURSES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "too many courses to aggregate");
        }
        LearningMetricsSnapshot snapshot =
                courseIds.isEmpty()
                        ? emptySnapshot(safeDays)
                        : learningAnalyticsClient.teacherMetrics(courseIds, safeDays);
        Map<Long, Course> owned =
                courses.stream()
                        .collect(
                                Collectors.toMap(
                                        Course::getId, Function.identity(), (left, right) -> left));

        long learningPairs = 0;
        long completedPairs = 0;
        for (LearningMetricsSnapshot.Course row : snapshot.courses()) {
            Course course = owned.get(row.courseId());
            if (course == null) {
                continue;
            }
            learningPairs += row.learningStudentCount();
            completedPairs += completedAll(row.completedLessonHistogram(), lessonCount(course));
        }
        long published =
                courses.stream()
                        .filter(course -> Objects.equals(course.getStatus(), STATUS_PUBLISHED))
                        .count();
        LearningMetricsSnapshot.Overview aggregate = snapshot.overview();
        return new TeacherAnalyticsOverviewView(
                safeDays,
                LocalDate.now().minusDays(safeDays - 1L),
                LocalDate.now(),
                courses.size(),
                published,
                aggregate.studentCount(),
                aggregate.learningStudentCount(),
                aggregate.activeStudentCount(),
                aggregate.newStudentCount(),
                aggregate.revokedStudentCount(),
                aggregate.cumulativeWatchedSeconds(),
                aggregate.completedLessons(),
                completedPairs,
                learningPairs,
                percent(completedPairs, learningPairs),
                aggregate.lastLearnedAt(),
                aggregate.dailyActiveStudents().stream()
                        .map(
                                point ->
                                        new TeacherAnalyticsOverviewView.DailyPoint(
                                                point.date(), point.studentCount()))
                        .toList());
    }

    /**
     * Returns one page of the teacher's course analytics.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param days requested natural day window
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @return page of course rows
     * @author Caspian
     * @since 0.16.0
     */
    @Override
    public TeacherCourseAnalyticsPageView courses(
            long userId, String roles, int days, int pageNum, int pageSize) {
        long teacherId = requireTeacherId(userId, roles);
        int safeDays = clampDays(days);
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        List<Course> all = teacherCourseMapper.selectAnalyticsCoursesByOwner(teacherId);
        long total = all.size();
        int from = (int) Math.min((long) (safePageNum - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, (int) total);
        if (from >= to) {
            return new TeacherCourseAnalyticsPageView(safePageNum, safePageSize, total, List.of());
        }
        List<Course> page = all.subList(from, to);
        LearningMetricsSnapshot snapshot =
                learningAnalyticsClient.teacherMetrics(
                        page.stream().map(Course::getId).toList(), safeDays);
        Map<Long, LearningMetricsSnapshot.Course> metrics =
                snapshot.courses().stream()
                        .collect(
                                Collectors.toMap(
                                        LearningMetricsSnapshot.Course::courseId,
                                        Function.identity(),
                                        (left, right) -> left));
        List<TeacherCourseAnalyticsView> records = new ArrayList<>();
        for (Course course : page) {
            records.add(toView(course, metrics.get(course.getId())));
        }
        return new TeacherCourseAnalyticsPageView(safePageNum, safePageSize, total, records);
    }

    /**
     * Returns the progress distribution and lesson completion of one owned course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course id
     * @param days requested natural day window
     * @return course detail
     * @author Caspian
     * @since 0.16.0
     */
    @Override
    public TeacherCourseAnalyticsDetailView course(
            long userId, String roles, long courseId, int days) {
        long teacherId = requireTeacherId(userId, roles);
        int safeDays = clampDays(days);
        Course course = teacherCourseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(course.getOwnerTeacherId(), teacherId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        List<CourseLesson> visibleLessons =
                teacherCourseMapper.selectLessons(courseId).stream()
                        .filter(lesson -> Objects.equals(lesson.getStatus(), STATUS_VISIBLE_LESSON))
                        .toList();
        int lessons = visibleLessons.size();
        LearningMetricsSnapshot snapshot =
                learningAnalyticsClient.teacherMetrics(List.of(courseId), safeDays);
        LearningMetricsSnapshot.Course row =
                snapshot.courses().stream()
                        .filter(item -> item.courseId() == courseId)
                        .findFirst()
                        .orElse(null);
        long learningStudents = row == null ? 0 : row.learningStudentCount();
        long completedStudents =
                row == null ? 0 : completedAll(row.completedLessonHistogram(), lessons);
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (String bucket : BUCKETS) {
            buckets.put(bucket, 0L);
        }
        if (row != null) {
            long startedWithCompletion =
                    row.completedLessonHistogram().stream()
                            .mapToLong(LearningMetricsSnapshot.HistogramEntry::studentCount)
                            .sum();
            buckets.put("not-started", Math.max(0, learningStudents - startedWithCompletion));
            for (LearningMetricsSnapshot.HistogramEntry entry : row.completedLessonHistogram()) {
                buckets.merge(
                        bucketOf(lessons, entry.completedLessons()),
                        entry.studentCount(),
                        Long::sum);
            }
        } else {
            buckets.put("not-started", learningStudents);
        }
        List<TeacherCourseAnalyticsDetailView.ProgressBucket> distribution =
                buckets.entrySet().stream()
                        .map(
                                entry ->
                                        new TeacherCourseAnalyticsDetailView.ProgressBucket(
                                                entry.getKey(), entry.getValue()))
                        .toList();

        Map<Long, LearningMetricsSnapshot.Lesson> lessonMetrics = new HashMap<>();
        if (row != null) {
            for (LearningMetricsSnapshot.Lesson lesson : row.lessons()) {
                lessonMetrics.put(lesson.lessonId(), lesson);
            }
        }
        List<TeacherCourseAnalyticsDetailView.LessonRow> lessonRows = new ArrayList<>();
        for (CourseLesson lesson : visibleLessons) {
            LearningMetricsSnapshot.Lesson metric = lessonMetrics.get(lesson.getId());
            long learners = metric == null ? 0 : metric.learners();
            long completed = metric == null ? 0 : metric.completedStudents();
            lessonRows.add(
                    new TeacherCourseAnalyticsDetailView.LessonRow(
                            lesson.getId(),
                            lesson.getTitle(),
                            learners,
                            completed,
                            percent(completed, learners)));
        }

        return new TeacherCourseAnalyticsDetailView(
                courseId,
                course.getTitle(),
                course.getStatus() == null ? 0 : course.getStatus(),
                lessons,
                row == null ? 0 : row.studentCount(),
                learningStudents,
                row == null ? 0 : row.activeStudentCount(),
                row == null ? 0 : row.newStudentCount(),
                row == null ? 0 : row.revokedStudentCount(),
                row == null ? 0 : row.cumulativeWatchedSeconds(),
                row == null ? 0 : row.completedLessons(),
                completedStudents,
                percent(completedStudents, learningStudents),
                row == null ? null : row.lastLearnedAt(),
                distribution,
                lessonRows);
    }

    private TeacherCourseAnalyticsView toView(
            Course course, LearningMetricsSnapshot.Course metric) {
        int lessons = lessonCount(course);
        long learningStudents = metric == null ? 0 : metric.learningStudentCount();
        long completedStudents =
                metric == null ? 0 : completedAll(metric.completedLessonHistogram(), lessons);
        return new TeacherCourseAnalyticsView(
                course.getId(),
                course.getTitle(),
                course.getStatus() == null ? 0 : course.getStatus(),
                lessons,
                metric == null ? 0 : metric.studentCount(),
                learningStudents,
                metric == null ? 0 : metric.activeStudentCount(),
                metric == null ? 0 : metric.newStudentCount(),
                metric == null ? 0 : metric.revokedStudentCount(),
                metric == null ? 0 : metric.cumulativeWatchedSeconds(),
                metric == null ? 0 : metric.completedLessons(),
                completedStudents,
                percent(completedStudents, learningStudents),
                metric == null ? null : metric.lastLearnedAt());
    }

    private long completedAll(List<LearningMetricsSnapshot.HistogramEntry> histogram, int lessons) {
        if (lessons <= 0 || histogram == null) {
            return 0;
        }
        return histogram.stream()
                .filter(entry -> entry.completedLessons() >= lessons)
                .mapToLong(LearningMetricsSnapshot.HistogramEntry::studentCount)
                .sum();
    }

    private String bucketOf(int lessons, int completedLessons) {
        if (lessons <= 0) {
            return "0-24";
        }
        int value = (int) Math.min(FULL_PERCENT, completedLessons * 100L / lessons);
        if (value >= FULL_PERCENT) {
            return "100";
        }
        if (value >= 75) {
            return "75-99";
        }
        if (value >= 50) {
            return "50-74";
        }
        if (value >= 25) {
            return "25-49";
        }
        return "0-24";
    }

    private Integer percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return (int) Math.min(FULL_PERCENT, Math.round(numerator * 100.0 / denominator));
    }

    private int lessonCount(Course course) {
        return course.getLessonCount() == null ? 0 : course.getLessonCount().intValue();
    }

    private int clampDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(Math.max(days, MIN_DAYS), MAX_DAYS);
    }

    private long requireTeacherId(long userId, String roles) {
        if (!hasRole(roles, "ADMIN") && !hasRole(roles, "TEACHER")) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Long teacherId = teacherCourseMapper.selectTeacherIdByUser(userId);
        if (teacherId == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "teacher profile is required");
        }
        return teacherId;
    }

    private boolean hasRole(String roles, String expected) {
        if (roles == null) {
            return false;
        }
        for (String role : roles.split(",")) {
            if (expected.equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private LearningMetricsSnapshot emptySnapshot(int days) {
        List<LearningMetricsSnapshot.DailyPoint> series = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (LocalDate date = today.minusDays(days - 1L);
                !date.isAfter(today);
                date = date.plusDays(1)) {
            series.add(new LearningMetricsSnapshot.DailyPoint(date, 0));
        }
        return new LearningMetricsSnapshot(
                new LearningMetricsSnapshot.Overview(0, 0, 0, 0, 0, 0, 0, null, series), List.of());
    }
}
