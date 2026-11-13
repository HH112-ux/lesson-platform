package com.lp.course.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Local mirror of the learn-service teacher analytics payload.
 *
 * <p>The learn-service module is not a compile dependency of the course-service, so the internal
 * contract is duplicated here. Field names must stay identical to {@code
 * TeacherLearningMetricsView} in the learn-service; a mismatch fails deserialization loudly instead
 * of silently returning zeros.
 *
 * @param overview teacher wide aggregates
 * @param courses per-course aggregates
 * @author Caspian
 * @since 0.16.0
 */
public record LearningMetricsSnapshot(Overview overview, List<Course> courses) {

    /**
     * Teacher wide aggregates.
     *
     * @param studentCount distinct students with an active entitlement
     * @param learningStudentCount distinct students with at least one progress row
     * @param activeStudentCount distinct students active in the window
     * @param newStudentCount distinct students granted in the window
     * @param revokedStudentCount distinct students revoked in the window
     * @param cumulativeWatchedSeconds lifetime effective watch seconds
     * @param completedLessons completed lesson rows
     * @param lastLearnedAt latest progress update
     * @param dailyActiveStudents daily active series
     * @author Caspian
     * @since 0.16.0
     */
    public record Overview(
            long studentCount,
            long learningStudentCount,
            long activeStudentCount,
            long newStudentCount,
            long revokedStudentCount,
            long cumulativeWatchedSeconds,
            long completedLessons,
            LocalDateTime lastLearnedAt,
            List<DailyPoint> dailyActiveStudents) {}

    /**
     * One point of the daily active series.
     *
     * @param date natural day
     * @param studentCount distinct active students
     * @author Caspian
     * @since 0.16.0
     */
    public record DailyPoint(LocalDate date, long studentCount) {}

    /**
     * Aggregates of one course.
     *
     * @param courseId course id
     * @param studentCount distinct students with an active entitlement
     * @param learningStudentCount distinct students with at least one progress row
     * @param activeStudentCount distinct students active in the window
     * @param newStudentCount distinct students granted in the window
     * @param revokedStudentCount distinct students revoked in the window
     * @param cumulativeWatchedSeconds lifetime effective watch seconds
     * @param completedLessons completed lesson rows
     * @param lastLearnedAt latest progress update
     * @param completedLessonHistogram students grouped by completed lesson count
     * @param lessons per-lesson counters
     * @author Caspian
     * @since 0.16.0
     */
    public record Course(
            long courseId,
            long studentCount,
            long learningStudentCount,
            long activeStudentCount,
            long newStudentCount,
            long revokedStudentCount,
            long cumulativeWatchedSeconds,
            long completedLessons,
            LocalDateTime lastLearnedAt,
            List<HistogramEntry> completedLessonHistogram,
            List<Lesson> lessons) {}

    /**
     * One histogram bucket.
     *
     * @param completedLessons completed lesson count
     * @param studentCount students in the bucket
     * @author Caspian
     * @since 0.16.0
     */
    public record HistogramEntry(int completedLessons, long studentCount) {}

    /**
     * Completion counters of one lesson.
     *
     * @param lessonId lesson id
     * @param learners distinct students that reported progress
     * @param completedStudents distinct students that completed the lesson
     * @author Caspian
     * @since 0.16.0
     */
    public record Lesson(long lessonId, long learners, long completedStudents) {}
}
