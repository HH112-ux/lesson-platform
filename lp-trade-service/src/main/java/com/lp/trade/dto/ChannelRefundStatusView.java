package com.lp.trade.dto;

import java.time.LocalDateTime;

/**
 * Read-only snapshot of one refund as reported by the channel.
 *
 * <p>Reading it performs one channel query and changes no local row. A missing record or a failed
 * query is reported through {@link #channelOutcome()}, never as an empty success or a {@code 404}.
 *
 * @param refundId refund primary key
 * @param channelOutcome {@link #CONFIRMED}, {@link #NOT_FOUND}, {@link #OTHER}, {@link
 *     #QUERY_FAILED} or {@link #UNAVAILABLE}
 * @param channelRefundStatus channel status, {@code null} when the channel reported none
 * @param channelRefundAmountFen channel refund amount in fen, {@code null} when not reported
 * @param channelRefundNo channel refund number stored locally, {@code null} before success
 * @param queriedAt when the channel was queried, {@code null} when it was not queried
 * @param queryMessage reason when the outcome is not {@link #CONFIRMED}, else {@code null}
 * @param reconcileFinding last finding stored by the reconciliation scan
 * @param reconcileCheckedAt when the scan last queried the channel
 * @param reconcileAttempts how many times the scan queried the channel
 * @author Caspian
 * @since 0.9.0
 */
public record ChannelRefundStatusView(
        long refundId,
        String channelOutcome,
        String channelRefundStatus,
        Long channelRefundAmountFen,
        String channelRefundNo,
        LocalDateTime queriedAt,
        String queryMessage,
        int reconcileFinding,
        LocalDateTime reconcileCheckedAt,
        int reconcileAttempts) {

    /** The channel confirmed a refund for the requested amount. */
    public static final String CONFIRMED = "CONFIRMED";

    /** The channel has no refund under the local request number. */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** The channel answered with another status or another amount. */
    public static final String OTHER = "OTHER";

    /** The channel query itself failed, so the outcome is unknown. */
    public static final String QUERY_FAILED = "QUERY_FAILED";

    /** The local payment is missing or not successful, so the channel was not asked. */
    public static final String UNAVAILABLE = "UNAVAILABLE";
}
