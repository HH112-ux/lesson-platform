package com.lp.course.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Teacher level learning dashboard summary.
 *
 * <p>Field semantics are frozen by the 2026-11-13 decision: {@code cumulativeWatchedSeconds} is a
 * lifetime sum and never a window increment, and {@code completionRatePercent} uses the learning
 * student count as its denominator.
 *
 * @param windowDays effective natural day window
 * @param startDate first day of the window, inclusive
 * @param endDate last day of the window, inclusive
 * @param courseCount every course owned by the teacher
 * @param publishedCourseCount owned courses in the published status
 * @param studentCount distinct students with an active entitlement
 * @param learningStudentCount distinct students with at least one progress row
 * @param activeStudentCount distinct students active in the window, a lower bound
 * @param newStudentCount distinct students granted in the window
 * @param revokedStudentCount distinct students revoked in the window
 * @param cumulativeWatchedSeconds lifetime effective watch seconds
 * @param completedLessons completed lesson rows
 * @param completedCourseStudents student-course pairs that finished every live lesson
 * @param learningCourseStudents student-course pairs with at least one progress row
 * @param completionRatePercent overall completion rate, {@code null} without learners
 * @param lastLearnedAt latest progress update, {@code null} when nothing was reported
 * @param dailyActiveStudents one point per window day, missing days filled with zero
 * @author Caspian
 * @since 0.16.0
 */
public record TeacherAnalyticsOverviewView(
        int windowDays,
        LocalDate startDate,
        LocalDate endDate,
        long courseCount,
        long publishedCourseCount,
        long studentCount,
        long learningStudentCount,
        long activeStudentCount,
        long newStudentCount,
        long revokedStudentCount,
        long cumulativeWatchedSeconds,
        long completedLessons,
        long completedCourseStudents,
        long learningCourseStudents,
        Integer completionRatePercent,
        LocalDateTime lastLearnedAt,
        List<DailyPoint> dailyActiveStudents) {

    /**
     * One point of the daily active series.
     *
     * @param date natural day
     * @param studentCount distinct active students, a lower bound
     * @author Caspian
     * @since 0.16.0
     */
    public record DailyPoint(LocalDate date, long studentCount) {}
}
