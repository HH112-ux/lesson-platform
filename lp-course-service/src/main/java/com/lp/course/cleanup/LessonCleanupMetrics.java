package com.lp.course.cleanup;

import com.lp.course.mapper.LessonContentCleanupMapper;
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
 * Publishes the cleanup queue backlog and the outcome counters as Micrometer meters.
 *
 * <p>Gauges are refreshed after every tick, so a Prometheus scrape never queries the database
 * itself. {@code pending} counts rows waiting for a tick, {@code failed.rows} counts terminal
 * failures, and {@code oldest.seconds} measures how long the oldest row that already spent an
 * attempt has been waiting: it only looks at {@code attempt_count > 0}, so a backlog of fresh rows
 * does not read as a stuck retry loop.
 *
 * <p>Counters cover what a tick did: {@code deleted} for rows that reached DONE, {@code retried}
 * for rows rescheduled after a transient failure and {@code failed} for rows that became terminal.
 * A deferred row is deliberately not counted: an object referenced forever is re-checked every
 * {@code defer-delay}, which would turn the counter into pure noise.
 *
 * @author Caspian
 * @since 0.17.0
 */
@Component
public class LessonCleanupMetrics {

    /** Gauge name: pending rows waiting for a tick. */
    static final String PENDING_METRIC = "lp.course.cleanup.pending";

    /** Gauge name: age in seconds of the oldest attempted, non-terminal row. */
    static final String OLDEST_METRIC = "lp.course.cleanup.oldest.seconds";

    /** Gauge name: rows in the terminal FAILED state. */
    static final String FAILED_ROWS_METRIC = "lp.course.cleanup.failed.rows";

    /** Counter name: rows deleted successfully. */
    static final String DELETED_METRIC = "lp.course.cleanup.deleted";

    /** Counter name: rows rescheduled after a transient failure. */
    static final String RETRIED_METRIC = "lp.course.cleanup.retried";

    /** Counter name: rows that reached the terminal FAILED state. */
    static final String FAILED_METRIC = "lp.course.cleanup.failed";

    private static final Logger log = LoggerFactory.getLogger(LessonCleanupMetrics.class);

    private final LessonContentCleanupMapper mapper;

    private final AtomicLong pending = new AtomicLong();

    private final AtomicLong oldestSeconds = new AtomicLong();

    private final AtomicLong failedRows = new AtomicLong();

    private final Counter deleted;

    private final Counter retried;

    private final Counter failed;

    /**
     * Registers the meters and performs a first refresh.
     *
     * @param mapper queue persistence used by the gauges
     * @param meterRegistry registry the meters are bound to
     * @author Caspian
     * @since 0.17.0
     */
    public LessonCleanupMetrics(LessonContentCleanupMapper mapper, MeterRegistry meterRegistry) {
        this.mapper = mapper;
        Gauge.builder(PENDING_METRIC, pending, AtomicLong::doubleValue)
                .description("Cleanup rows waiting for a tick")
                .register(meterRegistry);
        Gauge.builder(OLDEST_METRIC, oldestSeconds, AtomicLong::doubleValue)
                .description("Age of the oldest attempted, non-terminal cleanup row")
                .register(meterRegistry);
        Gauge.builder(FAILED_ROWS_METRIC, failedRows, AtomicLong::doubleValue)
                .description("Cleanup rows in the terminal FAILED state")
                .register(meterRegistry);
        this.deleted =
                Counter.builder(DELETED_METRIC)
                        .description("Cleanup rows whose object was deleted")
                        .register(meterRegistry);
        this.retried =
                Counter.builder(RETRIED_METRIC)
                        .description("Cleanup rows rescheduled after a transient failure")
                        .register(meterRegistry);
        this.failed =
                Counter.builder(FAILED_METRIC)
                        .description("Cleanup rows that reached the terminal FAILED state")
                        .register(meterRegistry);
        refresh();
    }

    /**
     * Reloads the three gauges; a failure is logged and leaves the previous values in place.
     *
     * @author Caspian
     * @since 0.17.0
     */
    void refresh() {
        try {
            pending.set(mapper.countPendingRows());
            failedRows.set(mapper.countFailedRows());
            LocalDateTime oldest = mapper.oldestAttemptedCreatedAt();
            oldestSeconds.set(oldest == null ? 0 : ageInSeconds(oldest));
        } catch (RuntimeException e) {
            log.warn("cleanup metrics refresh failed, keeping previous values", e);
        }
    }

    /**
     * Counts one row deleted successfully.
     *
     * @author Caspian
     * @since 0.17.0
     */
    void recordDeleted() {
        deleted.increment();
    }

    /**
     * Counts one row rescheduled after a transient failure.
     *
     * @author Caspian
     * @since 0.17.0
     */
    void recordRetried() {
        retried.increment();
    }

    /**
     * Counts one row that reached the terminal FAILED state.
     *
     * @author Caspian
     * @since 0.17.0
     */
    void recordFailed() {
        failed.increment();
    }

    private static long ageInSeconds(LocalDateTime oldest) {
        return Math.max(0, Duration.between(oldest, LocalDateTime.now()).getSeconds());
    }
}
