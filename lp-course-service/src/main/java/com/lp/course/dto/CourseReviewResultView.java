package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * Result of one review decision, mirroring the course state after the update.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseReviewResultView {

    private final long courseId;

    private final int status;

    private final long priceFen;

    private final LocalDateTime reviewedAt;

    /**
     * Creates the result.
     *
     * @param courseId course primary key
     * @param status resulting status, 2 approved or 0 rejected
     * @param priceFen price after the decision, in fen
     * @param reviewedAt review time
     * @author Caspian
     * @since 0.1.0
     */
    public CourseReviewResultView(
            long courseId, int status, long priceFen, LocalDateTime reviewedAt) {
        this.courseId = courseId;
        this.status = status;
        this.priceFen = priceFen;
        this.reviewedAt = reviewedAt;
    }

    /**
     * Returns the course id.
     *
     * @return course primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }

    /**
     * Returns the resulting status.
     *
     * @return 2 approved, 0 rejected
     * @author Caspian
     * @since 0.1.0
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the price after the decision.
     *
     * @return price in fen
     * @author Caspian
     * @since 0.1.0
     */
    public long getPriceFen() {
        return priceFen;
    }

    /**
     * Returns the review time.
     *
     * @return review time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }
}
