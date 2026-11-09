package com.lp.admin.dto;

import java.time.LocalDateTime;

/**
 * One unified audit entry shown in the operations console.
 *
 * @param id audit row id
 * @param operatorId operator user id
 * @param operatorRole operator role code, 0 operations, 1 administrator
 * @param actionType action code, 0 to 5
 * @param objectType object type code, 0 course, 1 refund, 2 teacher
 * @param objectId reviewed object primary key
 * @param fromStatus status before the decision
 * @param toStatus status after the decision
 * @param remark optional remark or rejection reason
 * @param traceId request trace id
 * @param createdAt time the decision happened
 * @author Caspian
 * @since 0.6.0
 */
public record AdminAuditLogView(
        long id,
        long operatorId,
        int operatorRole,
        int actionType,
        int objectType,
        long objectId,
        int fromStatus,
        int toStatus,
        String remark,
        String traceId,
        LocalDateTime createdAt) {}
