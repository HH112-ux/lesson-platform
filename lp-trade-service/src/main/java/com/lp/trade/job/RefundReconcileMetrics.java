package com.lp.trade.job;

import com.lp.trade.mapper.RefundRequestMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes the refund reconciliation backlog and findings as Micrometer meters.
 *
 * <p>Gauges are refreshed after every scan, so a Prometheus scrape never queries the database.
 * {@code pending} counts refunds whose channel outcome is still unsettled, {@code channel.done}
 * counts refunds the channel reports as refunded while the local row did not advance, and {@code
 * oldest.seconds} measures how long the oldest unsettled refund has been waiting. A non-zero {@code
 * channel.done} is the alerting signal: nobody can see a log, but a gauge that stays above zero
 * pages a human.
 *
 * <p>{@code channel.done} means "the channel confirmed the refund and the local row has not
 * advanced", whoever produced the finding. The review path writes the same finding right after a
 * synchronous channel success, so a brief non-zero value can be a review in flight; the alert text
 * must not claim the scan discovered it.
 *
 * <p>The {@code scanned} counter proves the scheduler is actually ticking, which the gauges alone
 * cannot show when no refund is pending. The {@code converged} counter proves the scan settled a
 * confirmed refund automatically; it stays zero while the convergence switch is off.
 *
 * @author Caspian
 * @since 0.7.0
 */
@Component
public class RefundReconcileMetrics {

    /** Gauge name: unsettled refunds (status approved or unknown). */
    static final String PENDING_METRIC = "lp.trade.refund.reconcile.pending";

    /** Gauge name: refunds the channel reports as done while the local row did not advance. */
    static final String CHANNEL_DONE_METRIC = "lp.trade.refund.reconcile.channel.done";

    /** Gauge name: age in seconds of the oldest unsettled refund. */
    static final String OLDEST_METRIC = "lp.trade.refund.reconcile.oldest.seconds";

    /** Counter name: channel read-backs attempted by the scan. */
    static final String SCANNED_METRIC = "lp.trade.refund.reconcile.scanned";

    /** Counter name: confirmed refunds settled automatically by the scan. */
    static final String CONVERGED_METRIC = "lp.trade.refund.reconcile.converged";

    private static final Logger LOG = LoggerFactory.getLogger(RefundReconcileMetrics.class);

    private final RefundRequestMapper refundRequestMapper;

    private final AtomicLong pending = new AtomicLong();

    private final AtomicLong channelDone = new AtomicLong();

    private final AtomicLong oldestSeconds = new AtomicLong();

    private final Counter scanned;

    private final Counter converged;

    /**
     * Registers the meters and performs a first refresh.
     *
     * @param refundRequestMapper refund persistence used by the gauges
     * @param meterRegistry registry the meters are bound to
     * @author Caspian
     * @since 0.7.0
     */
    public RefundReconcileMetrics(
            RefundRequestMapper refundRequestMapper, MeterRegistry meterRegistry) {
        this.refundRequestMapper = refundRequestMapper;
        Gauge.builder(PENDING_METRIC, pending, AtomicLong::doubleValue)
                .description("Refunds whose channel outcome is still unsettled")
                .register(meterRegistry);
        Gauge.builder(CHANNEL_DONE_METRIC, channelDone, AtomicLong::doubleValue)
                .description(
                        "Refunds confirmed by the channel but not advanced locally, including "
                                + "review settlements still in flight")
                .register(meterRegistry);
        Gauge.builder(OLDEST_METRIC, oldestSeconds, AtomicLong::doubleValue)
                .description("Age of the oldest unsettled refund")
                .register(meterRegistry);
        this.scanned =
                Counter.builder(SCANNED_METRIC)
                        .description("Channel read-backs attempted by the refund scan")
                        .register(meterRegistry);
        this.converged =
                Counter.builder(CONVERGED_METRIC)
                        .description("Confirmed refunds settled automatically by the refund scan")
                        .register(meterRegistry);
        refresh();
    }

    /**
     * Reloads the gauges; a failure is logged and leaves the previous values in place.
     *
     * @author Caspian
     * @since 0.7.0
     */
    void refresh() {
        try {
            pending.set(refundRequestMapper.countReconcilePending());
            channelDone.set(refundRequestMapper.countReconcileChannelDone());
            LocalDateTime oldest = refundRequestMapper.oldestReconcileCreatedAt();
            oldestSeconds.set(oldest == null ? 0 : ageInSeconds(oldest));
        } catch (RuntimeException exception) {
            LOG.warn("refund reconcile metrics refresh failed, keeping previous values", exception);
        }
    }

    /**
     * Counts one channel read-back attempt.
     *
     * @author Caspian
     * @since 0.7.0
     */
    void recordScanned() {
        scanned.increment();
    }

    /**
     * Counts one confirmed refund settled automatically by the scan.
     *
     * @author Caspian
     * @since 0.8.0
     */
    void recordConverged() {
        converged.increment();
    }

    private static long ageInSeconds(LocalDateTime oldest) {
        return Math.max(0, Duration.between(oldest, LocalDateTime.now()).getSeconds());
    }
}
