package com.lp.trade.dto;

import java.time.LocalDateTime;

/**
 * Payload posted to the course-service unified audit endpoint.
 *
 * @param operatorId operator user id
 * @param operatorRole operator role code, 0 operations, 1 administrator
 * @param actionType action code, 2 refund approve, 3 refund reject
 * @param objectType object type code, always 1 refund
 * @param objectId refund primary key
 * @param fromStatus status before the decision
 * @param toStatus status after the decision
 * @param remark optional rejection reason
 * @param traceId trace id of the review request
 * @param dedupeKey delivery idempotency key, repeated on every attempt
 * @param occurredAt time the decision happened
 * @author Caspian
 * @since 0.6.0
 */
public record AuditLogPayload(
        Long operatorId,
        Integer operatorRole,
        Integer actionType,
        Integer objectType,
        Long objectId,
        Integer fromStatus,
        Integer toStatus,
        String remark,
        String traceId,
        String dedupeKey,
        LocalDateTime occurredAt) {}
