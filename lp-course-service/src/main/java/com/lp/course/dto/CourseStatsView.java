package com.lp.course.dto;

/**
 * Course counts used by the operations dashboard.
 *
 * @param total live courses
 * @param draft courses in draft
 * @param pendingReview courses waiting for review
 * @param published published courses
 * @param offline courses taken offline
 * @author Caspian
 * @since 0.1.0
 */
public record CourseStatsView(
        long total, long draft, long pendingReview, long published, long offline) {}
