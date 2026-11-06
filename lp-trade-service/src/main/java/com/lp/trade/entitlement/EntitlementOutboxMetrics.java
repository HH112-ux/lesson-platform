package com.lp.trade.entitlement;

import com.lp.trade.mapper.EntitlementOutboxMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox backlog size as Micrometer gauges.
 *
 * <p>{@code entitlement_outbox_pending} and {@code entitlement_outbox_failed} are refreshed after
 * every relay batch and by a scheduled tick, so a scrape never has to query the database itself.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class EntitlementOutboxMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(EntitlementOutboxMetrics.class);

    private static final int PENDING_STATUS = 0;

    private static final int GIVEN_UP_STATUS = 2;

    private final EntitlementOutboxMapper entitlementOutboxMapper;

    private final AtomicLong pending = new AtomicLong();

    private final AtomicLong failed = new AtomicLong();

    /**
     * Registers the gauges and performs a first refresh.
     *
     * @param entitlementOutboxMapper outbox persistence
     * @param meterRegistry registry the gauges are bound to
     * @author Caspian
     * @since 0.1.0
     */
    public EntitlementOutboxMetrics(
            EntitlementOutboxMapper entitlementOutboxMapper, MeterRegistry meterRegistry) {
        this.entitlementOutboxMapper = entitlementOutboxMapper;
        Gauge.builder("entitlement_outbox_pending", pending, AtomicLong::doubleValue)
                .description("Entitlement outbox rows waiting for delivery")
                .register(meterRegistry);
        Gauge.builder("entitlement_outbox_failed", failed, AtomicLong::doubleValue)
                .description("Entitlement outbox rows given up after the retry limit")
                .register(meterRegistry);
        refresh();
    }

    /**
     * Reloads both counters; failure is logged and leaves the previous values in place.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Scheduled(fixedDelayString = "${lp.trade.outbox-metrics-delay-ms:15000}", initialDelay = 15000)
    public void refresh() {
        try {
            pending.set(entitlementOutboxMapper.countByStatus(PENDING_STATUS));
            failed.set(entitlementOutboxMapper.countByStatus(GIVEN_UP_STATUS));
        } catch (RuntimeException exception) {
            LOG.warn("entitlement outbox metric refresh failed: {}", exception.getMessage());
        }
    }

    /**
     * Returns the last observed pending count.
     *
     * @return pending row count
     * @author Caspian
     * @since 0.1.0
     */
    public long pendingCount() {
        return pending.get();
    }

    /**
     * Returns the last observed given up count.
     *
     * @return given up row count
     * @author Caspian
     * @since 0.1.0
     */
    public long failedCount() {
        return failed.get();
    }
}
