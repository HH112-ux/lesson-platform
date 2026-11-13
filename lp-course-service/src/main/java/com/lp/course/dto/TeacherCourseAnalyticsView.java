package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * One row of the teacher course analytics table.
 *
 * @param courseId course id
 * @param title course title
 * @param status course status, {@code 0} draft, {@code 1} pending, {@code 2} published, {@code 3}
 *     offline
 * @param lessonCount visible lesson count the completion rate is normalised against
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
 * @author Caspian
 * @since 0.16.0
 */
public record TeacherCourseAnalyticsView(
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
        LocalDateTime lastLearnedAt) {}
