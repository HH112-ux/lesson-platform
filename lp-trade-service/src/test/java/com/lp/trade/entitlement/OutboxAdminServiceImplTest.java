package com.lp.trade.entitlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.trade.dto.FailedOutboxPage;
import com.lp.trade.entity.EntitlementOutbox;
import com.lp.trade.mapper.EntitlementOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the outbox operations facade.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class OutboxAdminServiceImplTest {

    private static final long OUTBOX_ID = 41L;

    @Mock private EntitlementOutboxMapper entitlementOutboxMapper;

    @Mock private EntitlementOutboxMetrics metrics;

    private OutboxAdminServiceImpl service;

    /**
     * Builds the service with mocked persistence and metrics collaborators.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        service = new OutboxAdminServiceImpl(entitlementOutboxMapper, metrics);
    }

    /**
     * Given up rows are listed with their attempt count and last error.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void givenUpRowsAreListed() {
        when(entitlementOutboxMapper.countByStatus(2)).thenReturn(1L);
        when(entitlementOutboxMapper.selectGivenUp(0L, 20)).thenReturn(List.of(givenUp()));

        FailedOutboxPage page = service.failed(1, 20);

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(OUTBOX_ID, page.getRecords().get(0).getId());
        assertEquals(990001L, page.getRecords().get(0).getOrderId());
        assertEquals(10, page.getRecords().get(0).getRetryCount());
        assertEquals("upstream error code 50301", page.getRecords().get(0).getLastError());
    }

    /**
     * An empty backlog skips the row query entirely.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void emptyBacklogSkipsRowQuery() {
        when(entitlementOutboxMapper.countByStatus(2)).thenReturn(0L);

        FailedOutboxPage page = service.failed(1, 20);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verify(entitlementOutboxMapper, never()).selectGivenUp(0L, 20);
    }

    /**
     * Retrying a given up row reports success and refreshes the gauges.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void retryRequeuesGivenUpRow() {
        when(entitlementOutboxMapper.resetForRetry(OUTBOX_ID)).thenReturn(1);

        assertTrue(service.retry(OUTBOX_ID));
        verify(entitlementOutboxMapper).resetForRetry(OUTBOX_ID);
        verify(metrics).refresh();
    }

    /**
     * Retrying a row that is absent or not given up reports failure without touching gauges.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void retryRejectsUnknownRow() {
        when(entitlementOutboxMapper.resetForRetry(OUTBOX_ID)).thenReturn(0);

        assertFalse(service.retry(OUTBOX_ID));
        verify(metrics, never()).refresh();
    }

    /**
     * Gauges follow the counts returned by the mapper.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void gaugesFollowStatusCounts() {
        when(entitlementOutboxMapper.countByStatus(0)).thenReturn(3L);
        when(entitlementOutboxMapper.countByStatus(2)).thenReturn(2L);

        EntitlementOutboxMetrics realMetrics =
                new EntitlementOutboxMetrics(entitlementOutboxMapper, new SimpleMeterRegistry());

        assertEquals(3L, realMetrics.pendingCount());
        assertEquals(2L, realMetrics.failedCount());
    }

    private EntitlementOutbox givenUp() {
        EntitlementOutbox outbox = new EntitlementOutbox();
        outbox.setId(OUTBOX_ID);
        outbox.setOrderId(990001L);
        outbox.setOrderNo("LP202603010001");
        outbox.setUserId(1001L);
        outbox.setCourseId(7L);
        outbox.setStatus(2);
        outbox.setRetryCount(10);
        outbox.setLastError("upstream error code 50301");
        outbox.setUpdatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        return outbox;
    }
}
