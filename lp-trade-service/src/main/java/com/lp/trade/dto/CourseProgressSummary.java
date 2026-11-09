package com.lp.trade.dto;

/**
 * Course learning progress used by the refund admission rule.
 *
 * @param completedLessons completed lesson count reported by the learn service
 * @param lessonCount total lesson count of the course
 * @param progressPercent completed share in percent, rounded down
 * @author Caspian
 * @since 0.1.0
 */
public record CourseProgressSummary(int completedLessons, int lessonCount, int progressPercent) {}
