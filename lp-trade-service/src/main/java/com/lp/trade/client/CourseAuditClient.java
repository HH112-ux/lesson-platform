package com.lp.trade.client;

import com.lp.trade.entity.AuditOutbox;

/**
 * Delivers one refund audit intent to the unified audit table.
 *
 * <p>Implemented over the course-service internal API. The call is at-least-once: the relay repeats
 * the same payload, including {@code dedupeKey}, until course-service answers success, and the
 * target unique key absorbs a duplicate delivery.
 *
 * @author Caspian
 * @since 0.6.0
 */
public interface CourseAuditClient {

    /**
     * Appends one audit intent.
     *
     * @param outbox intent to deliver
     * @throws com.lp.common.exception.BizException when the upstream call fails
     * @author Caspian
     * @since 0.6.0
     */
    void append(AuditOutbox outbox);
}
