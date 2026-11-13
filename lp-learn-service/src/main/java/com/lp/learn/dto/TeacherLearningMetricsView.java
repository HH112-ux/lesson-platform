package com.lp.learn.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Learning aggregates exposed to the course-service teacher dashboard.
 *
 * <p>Values are raw counters. Percentages, buckets and completion rates are computed by the
 * course-service because they need the live lesson count, which lives in the course domain.
 *
 * @param overview teacher wide aggregates
 * @param courses per-course aggregates, ordered by course id
 * @author Caspian
 * @since 0.16.0
 */
public record TeacherLearningMetricsView(Overview overview, List<Course> courses) {

    /**
     * Teacher wide aggregates.
     *
     * @param studentCount distinct students with an active entitlement
     * @param learningStudentCount distinct students with at least one progress row
     * @param activeStudentCount distinct students active inside the window, a lower bound
     * @param newStudentCount distinct students granted inside the window
     * @param revokedStudentCount distinct students revoked inside the window
     * @param cumulativeWatchedSeconds lifetime effective watch seconds, not a window increment
     * @param completedLessons completed lesson rows
     * @param lastLearnedAt latest progress update, {@code null} when nothing was reported
     * @param dailyActiveStudents one point per natural day of the window, missing days filled with
     *     0
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
     * @param studentCount distinct active students, a lower bound
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
     * @param activeStudentCount distinct students active inside the window
     * @param newStudentCount distinct students granted inside the window
     * @param revokedStudentCount distinct students revoked inside the window
     * @param cumulativeWatchedSeconds lifetime effective watch seconds
     * @param completedLessons completed lesson rows
     * @param lastLearnedAt latest progress update, {@code null} when nothing was reported
     * @param completedLessonHistogram students grouped by their completed lesson count
     * @param lessons per-lesson completion counters
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
     * @param completedLessons completed lesson count of the bucket
     * @param studentCount number of students in the bucket
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
