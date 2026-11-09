package com.lp.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One refund row shown on the operations console.
 *
 * @param refundId refund primary key
 * @param refundNo refund number
 * @param orderNo business order number
 * @param userId buyer id
 * @param channel payment channel
 * @param amount refund amount in yuan
 * @param reason buyer supplied reason
 * @param status status from {@code RefundStatus}
 * @param reviewReason operations comment or rejection reason
 * @param failReason channel failure reason
 * @param reviewedBy reviewer user id
 * @param reviewedAt review time
 * @param refundedAt refund time
 * @param createdAt application time
 * @author Caspian
 * @since 0.1.0
 */
public record RefundAdminView(
        long refundId,
        String refundNo,
        String orderNo,
        long userId,
        int channel,
        BigDecimal amount,
        String reason,
        int status,
        String reviewReason,
        String failReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime refundedAt,
        LocalDateTime createdAt) {}
