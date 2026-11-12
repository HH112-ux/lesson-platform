package com.lp.admin.dto;

import java.time.LocalDateTime;

/**
 * Read-only channel snapshot of one refund, mirrored from trade-service.
 *
 * @param refundId refund primary key
 * @param channelOutcome {@code CONFIRMED}, {@code NOT_FOUND}, {@code OTHER}, {@code QUERY_FAILED}
 *     or {@code UNAVAILABLE}
 * @param channelRefundStatus channel status, {@code null} when not reported
 * @param channelRefundAmountFen channel refund amount in fen, {@code null} when not reported
 * @param channelRefundNo channel refund number stored locally
 * @param queriedAt when the channel was queried, {@code null} when it was not queried
 * @param queryMessage reason when the outcome is not {@code CONFIRMED}
 * @param reconcileFinding last finding stored by the reconciliation scan
 * @param reconcileCheckedAt when the scan last queried the channel
 * @param reconcileAttempts how many times the scan queried the channel
 * @author Caspian
 * @since 0.9.0
 */
public record AdminChannelRefundStatusView(
        long refundId,
        String channelOutcome,
        String channelRefundStatus,
        Long channelRefundAmountFen,
        String channelRefundNo,
        LocalDateTime queriedAt,
        String queryMessage,
        int reconcileFinding,
        LocalDateTime reconcileCheckedAt,
        int reconcileAttempts) {}
