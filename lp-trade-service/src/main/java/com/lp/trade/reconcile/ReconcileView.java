package com.lp.trade.reconcile;

import com.lp.trade.entity.PaymentReconcile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model returned by the reconciliation endpoints.
 *
 * <p>The raw bill text and the difference JSON are intentionally excluded; differences are
 * inspected through the stored aggregate and its count and amount here.
 *
 * @param id primary key
 * @param channel payment channel
 * @param billDate bill day in Asia/Shanghai
 * @param billType 1 payment bill, 2 refund bill
 * @param channelCount channel bill line count
 * @param channelAmount channel amount in yuan
 * @param localCount local record count
 * @param localAmount local amount in yuan
 * @param diffCount difference count
 * @param diffAmount channel minus local, positive when the channel collected more
 * @param status 0 reconciling, 1 matched, 2 pending, 3 handled
 * @param handledBy operator who marked it handled, {@code null} unless status 3
 * @param handledAt handled time, {@code null} unless status 3
 * @author Caspian
 * @since 0.6.1
 */
public record ReconcileView(
        Long id,
        Integer channel,
        LocalDate billDate,
        Integer billType,
        int channelCount,
        BigDecimal channelAmount,
        int localCount,
        BigDecimal localAmount,
        int diffCount,
        BigDecimal diffAmount,
        Integer status,
        Long handledBy,
        LocalDateTime handledAt) {

    /**
     * Maps a persisted aggregate to the read model.
     *
     * @param reconcile persisted aggregate
     * @return read model
     * @author Caspian
     * @since 0.6.1
     */
    public static ReconcileView from(PaymentReconcile reconcile) {
        return new ReconcileView(
                reconcile.getId(),
                reconcile.getChannel(),
                reconcile.getBillDate(),
                reconcile.getBillType(),
                reconcile.getChannelCount(),
                reconcile.getChannelAmount(),
                reconcile.getLocalCount(),
                reconcile.getLocalAmount(),
                reconcile.getDiffCount(),
                reconcile.getDiffAmount(),
                reconcile.getStatus(),
                reconcile.getHandledBy(),
                reconcile.getHandledAt());
    }
}
