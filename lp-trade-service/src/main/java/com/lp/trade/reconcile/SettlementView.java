package com.lp.trade.reconcile;

import com.lp.trade.entity.PaymentSettleBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model returned by the settlement endpoints.
 *
 * <p>Amounts are integers in fen so a client never parses a decimal money string. The batch is a
 * day-end seal plus a net summary: it records the channel and local totals of the payment and the
 * refund bill and their net amount. It never books a channel fee, never splits a payment and never
 * settles a teacher share.
 *
 * @param id primary key
 * @param channel payment channel
 * @param billDate bill day in Asia/Shanghai
 * @param channelPayCount payment lines in the channel bill
 * @param channelPayAmountFen channel payment amount in fen
 * @param channelRefundCount refund lines in the channel bill
 * @param channelRefundAmountFen channel refund amount in fen
 * @param localPayCount local successful payment records of the day
 * @param localPayAmountFen local payment amount in fen
 * @param localRefundCount local successful refund records of the day
 * @param localRefundAmountFen local refund amount in fen
 * @param netChannelAmountFen channel payment minus channel refund, in fen
 * @param netLocalAmountFen local payment minus local refund, in fen
 * @param diffCount sum of the difference counts of both bills
 * @param diffAmountFen sum of the difference amounts of both bills, in fen
 * @param status 1 closed, 2 voided
 * @param closedBy operator who closed the batch
 * @param closedAt close time
 * @param voidBy operator who voided the batch, {@code null} unless status 2
 * @param voidAt void time, {@code null} unless status 2
 * @author Caspian
 * @since 0.6.2
 */
public record SettlementView(
        Long id,
        Integer channel,
        LocalDate billDate,
        int channelPayCount,
        long channelPayAmountFen,
        int channelRefundCount,
        long channelRefundAmountFen,
        int localPayCount,
        long localPayAmountFen,
        int localRefundCount,
        long localRefundAmountFen,
        long netChannelAmountFen,
        long netLocalAmountFen,
        int diffCount,
        long diffAmountFen,
        Integer status,
        Long closedBy,
        LocalDateTime closedAt,
        Long voidBy,
        LocalDateTime voidAt) {

    /**
     * Maps a persisted batch to the read model.
     *
     * @param batch persisted batch
     * @return read model with every amount converted to fen
     * @author Caspian
     * @since 0.6.2
     */
    public static SettlementView from(PaymentSettleBatch batch) {
        return new SettlementView(
                batch.getId(),
                batch.getChannel(),
                batch.getBillDate(),
                batch.getChannelPayCount(),
                fen(batch.getChannelPayAmount()),
                batch.getChannelRefundCount(),
                fen(batch.getChannelRefundAmount()),
                batch.getLocalPayCount(),
                fen(batch.getLocalPayAmount()),
                batch.getLocalRefundCount(),
                fen(batch.getLocalRefundAmount()),
                fen(batch.getNetChannelAmount()),
                fen(batch.getNetLocalAmount()),
                batch.getDiffCount(),
                fen(batch.getDiffAmount()),
                batch.getStatus(),
                batch.getClosedBy(),
                batch.getClosedAt(),
                batch.getVoidBy(),
                batch.getVoidAt());
    }

    private static long fen(BigDecimal amount) {
        return amount == null ? 0L : amount.movePointRight(2).longValueExact();
    }
}
