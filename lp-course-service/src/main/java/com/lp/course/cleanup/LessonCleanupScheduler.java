package com.lp.course.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link LessonContentCleanupWorker} from the Spring scheduler.
 *
 * <p>The bean only exists when {@code lp.course.cleanup.enabled} is {@code true}, which is also the
 * default; setting it to {@code false} removes the ticker without touching the queue, so rows stay
 * where they are and resume on the next start with the switch back on.
 *
 * <p>A fixed delay of 30 seconds is measured from the end of the previous tick, so a slow batch
 * never overlaps itself. Assumes a single application instance: the worker claims rows with a
 * conditional update, so a second instance would not delete the same object twice, but the batch
 * size and the stale threshold are tuned for one ticker. Scale out only after adding a distributed
 * lock or leader election.
 *
 * @author Caspian
 * @since 0.17.0
 */
@Component
@ConditionalOnProperty(
        prefix = "lp.course.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LessonCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(LessonCleanupScheduler.class);

    private final LessonContentCleanupWorker worker;

    private final LessonCleanupMetrics metrics;

    /**
     * Creates the scheduler around the worker and its meters.
     *
     * @param worker executes one cleanup tick
     * @param metrics gauges refreshed after every tick
     * @author Caspian
     * @since 0.17.0
     */
    public LessonCleanupScheduler(LessonContentCleanupWorker worker, LessonCleanupMetrics metrics) {
        this.worker = worker;
        this.metrics = metrics;
    }

    /**
     * Runs one tick and refreshes the gauges, never letting an exception escape into the scheduler.
     *
     * <p>The worker already isolates rows, so an exception reaching this method means the queue
     * itself is unavailable (for example the database is down). Logging it and waiting for the next
     * tick is enough; the scheduler would otherwise stop rescheduling on an uncaught exception.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Scheduled(fixedDelay = 30_000)
    public void run() {
        try {
            int claimed = worker.tick();
            if (claimed > 0) {
                log.debug("cleanup tick claimed {} rows", claimed);
            }
        } catch (RuntimeException e) {
            log.warn("cleanup tick failed, retrying on the next one", e);
        } finally {
            metrics.refresh();
        }
    }
}
