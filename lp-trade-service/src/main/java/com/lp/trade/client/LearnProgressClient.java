package com.lp.trade.client;

import com.lp.trade.dto.CourseProgressSummary;

/**
 * Reads course learning progress from the learn service for refund admission.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface LearnProgressClient {

    /**
     * Returns the learning progress of one buyer in one course.
     *
     * @param userId buyer id
     * @param courseId course id
     * @return completed lessons, lesson count and progress percent
     * @throws com.lp.common.exception.BizException with {@code 50201} when learn-service cannot
     *     answer; the caller must fail closed instead of admitting the refund
     * @author Caspian
     * @since 0.1.0
     */
    CourseProgressSummary progress(long userId, long courseId);
}
