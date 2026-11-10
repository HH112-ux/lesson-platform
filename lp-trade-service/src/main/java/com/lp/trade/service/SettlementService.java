package com.lp.trade.service;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentSettleBatch;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.mapper.PaymentSettleBatchMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes one settled bill day of the Alipay channel.
 *
 * <p>A close is a day-end batch seal plus a net summary: it reads the payment and the refund
 * reconciliation aggregates of the day, stores the channel and local totals and their net amount on
 * {@code payment_settle_batch}, and locks the day. It never books a channel fee, never splits a
 * payment, never settles a teacher share and never moves money.
 *
 * <p>Both bills of the day must have been imported first ({@code payment_reconcile.status} 1 to 3),
 * otherwise the call fails with {@code 62019}. A day with pending differences fails with {@code
 * 50204} and writes nothing. The unique key {@code uk_channel_date} makes the call idempotent: a
 * repeated close returns the stored batch and its primary key.
 *
 * @author Caspian
 * @since 0.6.2
 */
@Service
public class SettlementService {

    static final int CHANNEL_ALIPAY = 1;

    static final int BILL_TYPE_PAYMENT = 1;

    static final int BILL_TYPE_REFUND = 2;

    static final int STATUS_CLOSED = 1;

    private static final int STATUS_IMPORTED_MIN = 1;

    private static final int STATUS_IMPORTED_MAX = 3;

    private final PaymentReconcileMapper reconcileMapper;

    private final PaymentSettleBatchMapper settleBatchMapper;

    /**
     * Creates the settlement service.
     *
     * @param reconcileMapper reads the day's payment and refund aggregates
     * @param settleBatchMapper reads and writes the settle batch
     * @author Caspian
     * @since 0.6.2
     */
    public SettlementService(
            PaymentReconcileMapper reconcileMapper, PaymentSettleBatchMapper settleBatchMapper) {
        this.reconcileMapper = reconcileMapper;
        this.settleBatchMapper = settleBatchMapper;
    }

    /**
     * Closes the bill day and returns the stored batch.
     *
     * @param channel payment channel; only {@link #CHANNEL_ALIPAY} is settled in this release
     * @param billDate bill day in Asia/Shanghai
     * @param closedBy operator user id resolved from the gateway identity header
     * @return closed batch, or the existing batch when the day was already closed
     * @throws BizException with {@code 40001} when the channel or bill day is invalid, {@code
     *     62019} when a bill of the day was not imported, or {@code 50204} when differences are
     *     pending
     * @author Caspian
     * @since 0.6.2
     */
    @Transactional
    public PaymentSettleBatch close(Integer channel, LocalDate billDate, long closedBy) {
        validate(channel, billDate);
        PaymentReconcile payment = requireImported(channel, billDate, BILL_TYPE_PAYMENT);
        PaymentReconcile refund = requireImported(channel, billDate, BILL_TYPE_REFUND);
        if (reconcileMapper.countUnresolved(channel, billDate) > 0) {
            throw new BizException(
                    ErrorCode.RECONCILE_UNRESOLVED,
                    "reconciliation still has unresolved differences");
        }
        PaymentSettleBatch existing = settleBatchMapper.selectByKey(channel, billDate);
        if (existing != null && Objects.equals(existing.getStatus(), STATUS_CLOSED)) {
            return existing;
        }
        settleBatchMapper.upsert(build(channel, billDate, closedBy, payment, refund));
        return settleBatchMapper.selectByKey(channel, billDate);
    }

    private void validate(Integer channel, LocalDate billDate) {
        if (!Objects.equals(channel, CHANNEL_ALIPAY)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "only Alipay is settled");
        }
        if (billDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bill date is required");
        }
    }

    private PaymentReconcile requireImported(Integer channel, LocalDate billDate, int billType) {
        PaymentReconcile row = reconcileMapper.selectByKey(channel, billDate, billType);
        boolean imported =
                row != null
                        && row.getStatus() >= STATUS_IMPORTED_MIN
                        && row.getStatus() <= STATUS_IMPORTED_MAX;
        if (!imported) {
            throw new BizException(
                    ErrorCode.SETTLE_BILL_INCOMPLETE,
                    billType == BILL_TYPE_PAYMENT
                            ? "payment bill is not imported"
                            : "refund bill is not imported");
        }
        return row;
    }

    private PaymentSettleBatch build(
            Integer channel,
            LocalDate billDate,
            long closedBy,
            PaymentReconcile payment,
            PaymentReconcile refund) {
        PaymentSettleBatch batch = new PaymentSettleBatch();
        batch.setChannel(channel);
        batch.setBillDate(billDate);
        batch.setChannelPayCount(payment.getChannelCount());
        batch.setChannelPayAmount(amount(payment.getChannelAmount()));
        batch.setChannelRefundCount(refund.getChannelCount());
        batch.setChannelRefundAmount(amount(refund.getChannelAmount()));
        batch.setLocalPayCount(payment.getLocalCount());
        batch.setLocalPayAmount(amount(payment.getLocalAmount()));
        batch.setLocalRefundCount(refund.getLocalCount());
        batch.setLocalRefundAmount(amount(refund.getLocalAmount()));
        batch.setNetChannelAmount(
                amount(payment.getChannelAmount().subtract(refund.getChannelAmount())));
        batch.setNetLocalAmount(amount(payment.getLocalAmount().subtract(refund.getLocalAmount())));
        batch.setDiffCount(payment.getDiffCount() + refund.getDiffCount());
        batch.setDiffAmount(amount(payment.getDiffAmount().add(refund.getDiffAmount())));
        batch.setStatus(STATUS_CLOSED);
        batch.setClosedBy(closedBy);
        batch.setClosedAt(LocalDateTime.now());
        return batch;
    }

    private BigDecimal amount(BigDecimal value) {
        return value.setScale(2);
    }
}
