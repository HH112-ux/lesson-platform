package com.lp.trade.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.lp.trade.mapper.RefundRequestMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Checks the meter names and values the refund reconciliation alert rules rely on.
 *
 * @author Caspian
 * @since 0.7.0
 */
@ExtendWith(MockitoExtension.class)
class RefundReconcileMetricsTest {

    @Mock private RefundRequestMapper refundRequestMapper;

    private SimpleMeterRegistry registry;

    private RefundReconcileMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new RefundReconcileMetrics(refundRequestMapper, registry);
    }

    @Test
    void publishesBacklogAndChannelDoneGauges() {
        when(refundRequestMapper.countReconcilePending()).thenReturn(3L);
        when(refundRequestMapper.countReconcileChannelDone()).thenReturn(1L);
        when(refundRequestMapper.oldestReconcileCreatedAt())
                .thenReturn(LocalDateTime.now().minusHours(2));

        metrics.refresh();

        assertEquals(3.0, gauge(RefundReconcileMetrics.PENDING_METRIC));
        assertEquals(1.0, gauge(RefundReconcileMetrics.CHANNEL_DONE_METRIC));
        assertTrue(gauge(RefundReconcileMetrics.OLDEST_METRIC) >= 7200);
    }

    @Test
    void reportsNoAgeWhenNothingIsPending() {
        when(refundRequestMapper.oldestReconcileCreatedAt()).thenReturn(null);

        metrics.refresh();

        assertEquals(0.0, gauge(RefundReconcileMetrics.OLDEST_METRIC));
    }

    @Test
    void countsEveryScannedRefund() {
        metrics.recordScanned();
        metrics.recordScanned();

        assertEquals(2.0, registry.get(RefundReconcileMetrics.SCANNED_METRIC).counter().count());
    }

    @Test
    void countsAutomaticConvergences() {
        metrics.recordConverged();

        assertEquals(1.0, registry.get(RefundReconcileMetrics.CONVERGED_METRIC).counter().count());
    }

    @Test
    void countsSkippedConvergences() {
        metrics.recordSkipped();

        assertEquals(1.0, registry.get(RefundReconcileMetrics.SKIPPED_METRIC).counter().count());
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
