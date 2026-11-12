package com.lp.trade.job;

import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundReconcileFinding;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.payment.ChannelRefundState;
import com.lp.trade.payment.PaymentGateway;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reports refunds whose local state disagrees with the channel, without changing refund state.
 *
 * <p>Approved and unknown refunds are read back through the channel using the stored local refund
 * number, which is the same value the refund request sent as {@code out_request_no}. Nothing about
 * the refund, order, payment or entitlement changes here: "not found" is not proof that no refund
 * happened, so the final state of an unknown refund stays a human decision.
 *
 * <p>A refund that never settles is not polled every tick. The first read-back happens after the
 * grace period, the second one an hour later and every further one six hours later; each scan is
 * capped and serves the refunds queried least recently first. The bookkeeping columns are the only
 * write, and their status predicate keeps a settled refund untouched.
 *
 * <p>The job assumes a single trade-service instance: two instances would query the same refunds
 * twice.
 *
 * @author Caspian
 * @since 0.7.0
 */
@Component
public class RefundReconcileJob {

    private static final Logger LOG = LoggerFactory.getLogger(RefundReconcileJob.class);

    private static final int BATCH_SIZE = 50;

    private final RefundRequestMapper refundRequestMapper;

    private final PaymentMapper paymentMapper;

    private final PaymentGateway paymentGateway;

    private final long graceMinutes;

    /**
     * Creates the job with its read-only dependencies.
     *
     * @param refundRequestMapper refund persistence
     * @param paymentMapper payment persistence
     * @param paymentGateway channel adapter used for the read-only refund query
     * @param graceMinutes minutes a refund must stay unchanged before its first read-back
     * @author Caspian
     * @since 0.7.0
     */
    public RefundReconcileJob(
            RefundRequestMapper refundRequestMapper,
            PaymentMapper paymentMapper,
            PaymentGateway paymentGateway,
            @Value("${lp.trade.refund-reconcile-grace-minutes:10}") long graceMinutes) {
        this.refundRequestMapper = refundRequestMapper;
        this.paymentMapper = paymentMapper;
        this.paymentGateway = paymentGateway;
        this.graceMinutes = graceMinutes;
    }

    /**
     * Inspects one batch of unsettled refunds; never throws so the scheduler keeps running.
     *
     * @author Caspian
     * @since 0.7.0
     */
    @Scheduled(
            fixedDelayString = "${lp.trade.refund-reconcile-scan-delay-ms:600000}",
            initialDelayString = "${lp.trade.refund-reconcile-initial-delay-ms:120000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        List<RefundRequest> candidates;
        try {
            candidates =
                    refundRequestMapper.selectReconcileCandidates(
                            now.minusMinutes(graceMinutes),
                            now.minusHours(1),
                            now.minusHours(6),
                            BATCH_SIZE);
        } catch (RuntimeException exception) {
            LOG.warn("refund reconcile scan failed: {}", exception.getMessage());
            return;
        }
        for (RefundRequest refund : candidates) {
            try {
                inspect(refund, now);
            } catch (RuntimeException exception) {
                LOG.error(
                        "refund reconcile failed: refund={}, reason={}",
                        refund.getRefundNo(),
                        exception.getMessage());
            }
        }
    }

    private void inspect(RefundRequest refund, LocalDateTime checkedAt) {
        PaymentRecord payment = paymentMapper.selectById(refund.getPaymentId());
        if (payment == null || !Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS)) {
            LOG.warn(
                    "refund reconcile skipped, payment is not successful: refund={}, localStatus={}",
                    refund.getRefundNo(),
                    statusName(refund.getStatus()));
            record(refund, checkedAt, RefundReconcileFinding.PAYMENT_UNSETTLED);
            return;
        }
        ChannelRefundState state;
        try {
            state = paymentGateway.refundQuery(payment, refund.getRefundNo());
        } catch (RuntimeException exception) {
            LOG.warn(
                    "refund reconcile query failed, left unchanged: refund={}, localStatus={}, "
                            + "reason={}",
                    refund.getRefundNo(),
                    statusName(refund.getStatus()),
                    exception.getMessage());
            record(refund, checkedAt, RefundReconcileFinding.QUERY_FAILED);
            return;
        }
        if (state.succeeded() && state.amountMatches(refund.getAmount())) {
            LOG.warn(
                    "refund completed at channel but still {} locally, manual follow-up needed: "
                            + "refund={}, orderId={}, amount={}",
                    statusName(refund.getStatus()),
                    refund.getRefundNo(),
                    refund.getOrderId(),
                    refund.getAmount());
            record(refund, checkedAt, RefundReconcileFinding.CHANNEL_DONE);
            return;
        }
        if (state.refundStatus() == null) {
            LOG.warn(
                    "refund not found at channel, left unchanged: refund={}, localStatus={}",
                    refund.getRefundNo(),
                    statusName(refund.getStatus()));
            record(refund, checkedAt, RefundReconcileFinding.CHANNEL_NOT_FOUND);
            return;
        }
        LOG.warn(
                "refund channel state does not confirm the request, left unchanged: refund={}, "
                        + "localStatus={}, channelStatus={}, channelAmount={}, expected={}",
                refund.getRefundNo(),
                statusName(refund.getStatus()),
                state.refundStatus(),
                state.refundAmount(),
                refund.getAmount());
        record(refund, checkedAt, RefundReconcileFinding.CHANNEL_OTHER);
    }

    /**
     * Stores the finding and counts the attempt, which drives the backoff of the next scan.
     *
     * @param refund scanned refund
     * @param checkedAt query time
     * @param finding finding code of this query
     * @author Caspian
     * @since 0.7.0
     */
    private void record(RefundRequest refund, LocalDateTime checkedAt, int finding) {
        refundRequestMapper.markReconcileChecked(refund.getId(), finding, checkedAt);
    }

    private String statusName(int status) {
        if (status == RefundStatus.APPROVED) {
            return "APPROVED";
        }
        if (status == RefundStatus.UNKNOWN) {
            return "UNKNOWN";
        }
        return String.valueOf(status);
    }
}
