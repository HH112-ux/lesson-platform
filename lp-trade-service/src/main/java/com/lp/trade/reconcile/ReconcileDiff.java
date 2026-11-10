package com.lp.trade.reconcile;

import java.math.BigDecimal;

/**
 * One difference between the channel bill and the local trade records.
 *
 * <p>{@code matchStatus} is 2 when only the channel has the entry (long difference), 3 when only
 * the local side has it (short difference), and 4 when both exist but the amounts differ. A
 * positive {@code diffAmount} means the channel collected more. The local reference points at
 * {@code payment_record} or {@code refund_request} so operations can locate the exact row. This
 * record is stored as JSON and never triggers a fund movement.
 *
 * @param itemKey stable identity: {@code CHANNEL:<tradeNo>} or {@code LOCAL:<localRefId>}
 * @param channelTradeNo channel transaction number, {@code null} for a local-only entry
 * @param localRefType 1 for {@code payment_record}, 2 for {@code refund_request}
 * @param localRefId local row id, {@code null} for a channel-only entry
 * @param matchStatus 2 long difference, 3 short difference, 4 amount mismatch
 * @param channelAmount channel amount in yuan
 * @param localAmount local amount in yuan
 * @param diffAmount channel minus local, positive when the channel collected more
 * @author Caspian
 * @since 0.6.1
 */
public record ReconcileDiff(
        String itemKey,
        String channelTradeNo,
        Integer localRefType,
        Long localRefId,
        Integer matchStatus,
        BigDecimal channelAmount,
        BigDecimal localAmount,
        BigDecimal diffAmount) {}
