package com.lp.admin.dto;

import java.time.LocalDateTime;

/**
 * Course state after one review decision.
 *
 * @param courseId course primary key
 * @param status resulting status, 2 approved or 0 rejected
 * @param priceFen price after the decision, in fen
 * @param reviewedAt review time
 * @author Caspian
 * @since 0.1.0
 */
public record AdminReviewResultView(
        long courseId, int status, long priceFen, LocalDateTime reviewedAt) {}
