package com.lp.course.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Teacher view of a single course: the summary row plus progress distribution and per-lesson
 * completion.
 *
 * @param courseId course id
 * @param title course title
 * @param status course status
 * @param lessonCount visible lesson count
 * @param studentCount distinct students with an active entitlement
 * @param learningStudentCount distinct students with at least one progress row
 * @param activeStudentCount distinct students active in the window
 * @param newStudentCount distinct students granted in the window
 * @param revokedStudentCount distinct students revoked in the window
 * @param cumulativeWatchedSeconds lifetime effective watch seconds
 * @param completedLessonCount completed lesson rows
 * @param completedCourseStudents students that finished every visible lesson
 * @param completionRatePercent completion rate, {@code null} without learners
 * @param lastLearnedAt latest progress update, {@code null} when nothing was reported
 * @param progressDistribution fixed six bucket distribution, in display order
 * @param lessons visible lessons with their completion counters
 * @author Caspian
 * @since 0.16.0
 */
public record TeacherCourseAnalyticsDetailView(
        long courseId,
        String title,
        int status,
        long lessonCount,
        long studentCount,
        long learningStudentCount,
        long activeStudentCount,
        long newStudentCount,
        long revokedStudentCount,
        long cumulativeWatchedSeconds,
        long completedLessonCount,
        long completedCourseStudents,
        Integer completionRatePercent,
        LocalDateTime lastLearnedAt,
        List<ProgressBucket> progressDistribution,
        List<LessonRow> lessons) {

    /**
     * One progress bucket.
     *
     * @param bucket bucket key: {@code not-started}, {@code 0-24}, {@code 25-49}, {@code 50-74},
     *     {@code 75-99} or {@code 100}
     * @param studentCount number of learning students in the bucket
     * @author Caspian
     * @since 0.16.0
     */
    public record ProgressBucket(String bucket, long studentCount) {}

    /**
     * Completion counters of one lesson.
     *
     * @param lessonId lesson id
     * @param title lesson title
     * @param learners distinct students that reported progress
     * @param completedStudents distinct students that completed the lesson
     * @param completionRatePercent completion rate over learners, {@code null} without learners
     * @author Caspian
     * @since 0.16.0
     */
    public record LessonRow(
            long lessonId,
            String title,
            long learners,
            long completedStudents,
            Integer completionRatePercent) {}
}
