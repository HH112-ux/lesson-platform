package com.lp.trade.reconcile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of comparing one bill day against the local trade rows.
 *
 * @param channelCount channel bill line count
 * @param channelAmount channel amount in yuan
 * @param localCount local record count
 * @param localAmount local amount in yuan
 * @param diffCount difference count, long plus short
 * @param diffAmount channel minus local, positive when the channel collected more
 * @param diffs difference details sorted by item key
 * @author Caspian
 * @since 0.6.1
 */
public record ReconcileResult(
        int channelCount,
        BigDecimal channelAmount,
        int localCount,
        BigDecimal localAmount,
        int diffCount,
        BigDecimal diffAmount,
        List<ReconcileDiff> diffs) {}
