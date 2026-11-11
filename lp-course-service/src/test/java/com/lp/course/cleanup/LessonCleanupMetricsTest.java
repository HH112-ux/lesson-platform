package com.lp.course.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.lp.course.mapper.LessonContentCleanupMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Checks the meter names and the value each meter carries, since the alert rules match on them.
 *
 * @author Caspian
 * @since 0.17.0
 */
@ExtendWith(MockitoExtension.class)
class LessonCleanupMetricsTest {

    @Mock private LessonContentCleanupMapper mapper;

    private SimpleMeterRegistry registry;

    private LessonCleanupMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new LessonCleanupMetrics(mapper, registry);
    }

    /**
     * Gauges expose the three queue shapes the alert rules rely on.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void publishesBacklogAndFailureGauges() {
        when(mapper.countPendingRows()).thenReturn(4);
        when(mapper.countFailedRows()).thenReturn(2);
        when(mapper.oldestAttemptedCreatedAt()).thenReturn(LocalDateTime.now().minusMinutes(90));

        metrics.refresh();

        assertEquals(4.0, gauge(LessonCleanupMetrics.PENDING_METRIC));
        assertEquals(2.0, gauge(LessonCleanupMetrics.FAILED_ROWS_METRIC));
        assertTrue(gauge(LessonCleanupMetrics.OLDEST_METRIC) >= 5400);
    }

    /**
     * No attempted row means no age to report, not a stale value from an earlier tick.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void reportsZeroAgeWithoutAttemptedRows() {
        when(mapper.oldestAttemptedCreatedAt()).thenReturn(null);

        metrics.refresh();

        assertEquals(0.0, gauge(LessonCleanupMetrics.OLDEST_METRIC));
    }

    /**
     * A failed read keeps the last published values instead of resetting them to zero.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void keepsPreviousValuesWhenTheQueueCannotBeRead() {
        when(mapper.countPendingRows())
                .thenReturn(5)
                .thenThrow(new IllegalStateException("database is down"));

        metrics.refresh();
        metrics.refresh();

        assertEquals(5.0, gauge(LessonCleanupMetrics.PENDING_METRIC));
    }

    /**
     * Each outcome increments its own counter.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void countsOutcomes() {
        metrics.recordDeleted();
        metrics.recordDeleted();
        metrics.recordRetried();
        metrics.recordFailed();

        assertEquals(2.0, counter(LessonCleanupMetrics.DELETED_METRIC));
        assertEquals(1.0, counter(LessonCleanupMetrics.RETRIED_METRIC));
        assertEquals(1.0, counter(LessonCleanupMetrics.FAILED_METRIC));
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    private double counter(String name) {
        return registry.get(name).counter().count();
    }
}
