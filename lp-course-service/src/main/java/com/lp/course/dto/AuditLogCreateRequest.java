package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * One audit entry to append, sent by an internal caller or built in-process.
 *
 * @param operatorId operator user id
 * @param operatorRole operator role code, 0 operations, 1 administrator
 * @param actionType action code, 0 to 5
 * @param objectType object type code, 0 course, 1 refund, 2 teacher
 * @param objectId reviewed object primary key
 * @param fromStatus status before the decision
 * @param toStatus status after the decision
 * @param remark optional remark or rejection reason
 * @param traceId request trace id
 * @param dedupeKey optional delivery idempotency key, required when a relay may retry
 * @param occurredAt time the decision happened
 * @author Caspian
 * @since 0.6.0
 */
public record AuditLogCreateRequest(
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
