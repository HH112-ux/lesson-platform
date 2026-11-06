package com.lp.admin.dto;

/**
 * Course counts returned by course-service.
 *
 * @param total live courses
 * @param draft draft courses
 * @param pendingReview courses waiting for review
 * @param published published courses
 * @param offline offline courses
 * @author Caspian
 * @since 0.1.0
 */
public record AdminCourseStats(
        long total, long draft, long pendingReview, long published, long offline) {}
