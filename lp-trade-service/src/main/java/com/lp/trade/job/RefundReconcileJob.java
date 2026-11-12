package com.lp.trade.job;

import com.lp.common.money.Money;
import com.lp.trade.domain.PaymentStatus;
import com.lp.trade.domain.RefundReconcileFinding;
import com.lp.trade.domain.RefundStatus;
import com.lp.trade.entity.PaymentRecord;
import com.lp.trade.entity.RefundRequest;
import com.lp.trade.mapper.PaymentMapper;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.payment.ChannelRefundState;
import com.lp.trade.payment.PaymentGateway;
import com.lp.trade.service.RefundSettlementService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads refunds back from the channel and, when enabled, converges confirmed ones.
 *
 * <p>Approved and unknown refunds are read back through the channel using the stored local refund
 * number, which is the same value the refund request sent as {@code out_request_no}. A refund whose
 * channel outcome is not confirmed is never changed: "not found" is not proof that no refund
 * happened, so the final state of an unknown refund stays a human decision.
 *
 * <p>A read-back that confirms the refund stores the same {@code CHANNEL_DONE} finding the review
 * path writes. That finding means "the channel confirmed the refund and the local row has not
 * advanced", whoever produced it, so it is not a reconciliation-only signal.
 *
 * <p>Automatic convergence is off by default. When {@code lp.pay.alipay.refund-converge.enabled} is
 * {@code true}, a confirmed refund is settled through {@link RefundSettlementService}, the same
 * gate the review path uses. While the switch is off, a confirmed refund stays unsettled and the
 * {@code channel.done} gauge is the only safety net.
 *
 * <p>A refund that never settles is not polled every tick. The first read-back happens after the
 * grace period, the second one an hour later and every further one six hours later; each scan is
 * capped and serves the refunds queried least recently first. A finding is logged only when it
 * differs from the previous one, while every query is counted, so a stuck refund does not turn the
 * log into noise.
 *
 * <p>The job assumes a single trade-service instance: two instances would query the same refunds
 * twice. Disable it with {@code lp.pay.alipay.refund-reconcile.enabled=false}.
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

    private final RefundReconcileMetrics metrics;

    private final RefundSettlementService settlementService;

    private final boolean enabled;

    private final boolean convergeEnabled;

    private final long graceMinutes;

    /**
     * Creates the job with its read and convergence dependencies.
     *
     * @param refundRequestMapper refund persistence
     * @param paymentMapper payment persistence
     * @param paymentGateway channel adapter used for the read-only refund query
     * @param metrics meters refreshed after every scan
     * @param settlementService shared settlement gate used only when convergence is enabled
     * @param enabled whether the scan runs at all
     * @param convergeEnabled whether a confirmed refund is settled automatically
     * @param graceMinutes minutes a refund must stay unchanged before its first read-back
     * @author Caspian
     * @since 0.7.0
     */
    public RefundReconcileJob(
            RefundRequestMapper refundRequestMapper,
            PaymentMapper paymentMapper,
            PaymentGateway paymentGateway,
            RefundReconcileMetrics metrics,
            RefundSettlementService settlementService,
            @Value("${lp.pay.alipay.refund-reconcile.enabled:true}") boolean enabled,
            @Value("${lp.pay.alipay.refund-converge.enabled:false}") boolean convergeEnabled,
            @Value("${lp.trade.refund-reconcile-grace-minutes:10}") long graceMinutes) {
        this.refundRequestMapper = refundRequestMapper;
        this.paymentMapper = paymentMapper;
        this.paymentGateway = paymentGateway;
        this.metrics = metrics;
        this.settlementService = settlementService;
        this.enabled = enabled;
        this.convergeEnabled = convergeEnabled;
        this.graceMinutes = graceMinutes;
    }

    /**
     * Inspects one batch of unsettled refunds; never throws so the scheduler keeps running.
     *
     * <p>Disabled by {@code lp.pay.alipay.refund-reconcile.enabled=false} so a multi-instance
     * deployment can keep duplicate reads away until a leader election exists.
     *
     * @author Caspian
     * @since 0.7.0
     */
    @Scheduled(
            fixedDelayString = "${lp.trade.refund-reconcile-scan-delay-ms:600000}",
            initialDelayString = "${lp.trade.refund-reconcile-initial-delay-ms:120000}")
    public void scan() {
        if (!enabled) {
            return;
        }
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
        metrics.refresh();
    }

    private void inspect(RefundRequest refund, LocalDateTime checkedAt) {
        PaymentRecord payment = paymentMapper.selectById(refund.getPaymentId());
        if (payment == null || !Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS)) {
            record(
                    refund,
                    checkedAt,
                    RefundReconcileFinding.PAYMENT_UNSETTLED,
                    () ->
                            LOG.warn(
                                    "refund reconcile skipped, payment is not successful: "
                                            + "refund={}, localStatus={}",
                                    refund.getRefundNo(),
                                    statusName(refund.getStatus())));
            return;
        }
        metrics.recordScanned();
        ChannelRefundState state;
        try {
            state = paymentGateway.refundQuery(payment, refund.getRefundNo());
        } catch (RuntimeException exception) {
            record(
                    refund,
                    checkedAt,
                    RefundReconcileFinding.QUERY_FAILED,
                    () ->
                            LOG.warn(
                                    "refund reconcile query failed, left unchanged: refund={}, "
                                            + "localStatus={}, reason={}",
                                    refund.getRefundNo(),
                                    statusName(refund.getStatus()),
                                    exception.getMessage()));
            return;
        }
        if (state.succeeded() && state.amountMatches(refund.getAmount())) {
            record(
                    refund,
                    checkedAt,
                    RefundReconcileFinding.CHANNEL_DONE,
                    () -> logChannelDone(refund));
            converge(refund);
            return;
        }
        if (state.refundStatus() == null) {
            record(
                    refund,
                    checkedAt,
                    RefundReconcileFinding.CHANNEL_NOT_FOUND,
                    () ->
                            LOG.warn(
                                    "refund not found at channel, left unchanged: refund={}, "
                                            + "localStatus={}",
                                    refund.getRefundNo(),
                                    statusName(refund.getStatus())));
            return;
        }
        record(
                refund,
                checkedAt,
                RefundReconcileFinding.CHANNEL_OTHER,
                () ->
                        LOG.warn(
                                "refund channel state does not confirm the request, left "
                                        + "unchanged: refund={}, localStatus={}, channelStatus={}, "
                                        + "channelAmount={}, expected={}",
                                refund.getRefundNo(),
                                statusName(refund.getStatus()),
                                state.refundStatus(),
                                state.refundAmount(),
                                refund.getAmount()));
    }

    /**
     * Logs a channel-confirmed refund, naming automatic convergence when it is switched on.
     *
     * @param refund scanned refund
     * @author Caspian
     * @since 0.8.0
     */
    private void logChannelDone(RefundRequest refund) {
        if (convergeEnabled) {
            LOG.warn(
                    "refund confirmed at channel but still {} locally, converging automatically: "
                            + "refund={}, orderId={}, amount={}",
                    statusName(refund.getStatus()),
                    refund.getRefundNo(),
                    refund.getOrderId(),
                    refund.getAmount());
            return;
        }
        LOG.warn(
                "refund confirmed at channel but still {} locally, automatic convergence is "
                        + "disabled: refund={}, orderId={}, amount={}",
                statusName(refund.getStatus()),
                refund.getRefundNo(),
                refund.getOrderId(),
                refund.getAmount());
    }

    /**
     * Settles one channel-confirmed refund through the shared gate when convergence is enabled.
     *
     * <p>The gate re-checks the row, so a row that moved since the read-back is left alone. A
     * rejected call returns {@code 0} and is normal, not an error.
     *
     * @param refund refund the channel confirmed
     * @author Caspian
     * @since 0.8.0
     */
    private void converge(RefundRequest refund) {
        if (!convergeEnabled) {
            return;
        }
        try {
            int settled =
                    settlementService.settle(
                            refund.getId(), Money.toFen(refund.getAmount()), null, "job");
            if (settled > 0) {
                metrics.recordConverged();
                LOG.info(
                        "refund converged automatically: refund={}, orderId={}",
                        refund.getRefundNo(),
                        refund.getOrderId());
            }
        } catch (RuntimeException exception) {
            LOG.error(
                    "refund convergence failed: refund={}, reason={}",
                    refund.getRefundNo(),
                    exception.getMessage());
        }
    }

    /**
     * Stores the finding and logs it only when it differs from the previous one.
     *
     * @param refund scanned refund
     * @param checkedAt query time
     * @param finding finding code of this query
     * @param log warning emitted only on a change
     * @author Caspian
     * @since 0.7.0
     */
    private void record(RefundRequest refund, LocalDateTime checkedAt, int finding, Runnable log) {
        if (!Integer.valueOf(finding).equals(refund.getReconcileFinding())) {
            log.run();
        }
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
