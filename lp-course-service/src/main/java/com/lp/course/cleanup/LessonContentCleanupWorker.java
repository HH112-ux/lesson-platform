package com.lp.course.cleanup;

import com.lp.course.config.LessonCleanupProperties;
import com.lp.course.mapper.LessonContentCleanupMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deletes the storage objects registered when lessons lose their video.
 *
 * <p>One tick reclaims rows abandoned by a crashed tick, reads a batch of due rows, and for each
 * one: claim it, re-check the references against the current state, then delete or reschedule. The
 * reference check runs here rather than at registration time because a lesson may have been
 * switched to another object and a transcode may have started in between.
 *
 * <p>Rows are handled in isolation: an unexpected runtime exception is logged and the rest of the
 * batch still runs. The failing row stays RUNNING and the next tick reclaims it once {@code
 * stale-after} elapses, so a single poisoned row cannot stall the queue.
 *
 * <p>An object still referenced by a live lesson, a pending upload session or an active transcode
 * is deferred forever on purpose. It keeps waiting, spends no attempt and raises no alert; do not
 * add a "maximum deferrals" rule that turns this correct state into a failure.
 *
 * <p>Assumes a single application instance. Claiming is a conditional update, so two instances
 * would not delete the same object twice, but the batch size and the stale-row threshold are tuned
 * for one ticker; scale out only after adding a distributed lock or leader election.
 *
 * @author Caspian
 * @since 0.17.0
 */
@Component
public class LessonContentCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(LessonContentCleanupWorker.class);

    private final LessonContentCleanupMapper mapper;
    private final LessonCleanupObjectStore store;
    private final LessonCleanupProperties properties;
    private final LessonCleanupMetrics metrics;

    /**
     * Creates the worker.
     *
     * @param mapper cleanup registry statements
     * @param store object storage deletion
     * @param properties retry and batch settings
     * @param metrics outcome counters incremented per handled row
     * @author Caspian
     * @since 0.17.0
     */
    public LessonContentCleanupWorker(
            LessonContentCleanupMapper mapper,
            LessonCleanupObjectStore store,
            LessonCleanupProperties properties,
            LessonCleanupMetrics metrics) {
        this.mapper = mapper;
        this.store = store;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Handles one batch of due rows.
     *
     * @return number of rows claimed by this tick
     * @author Caspian
     * @since 0.17.0
     */
    public int tick() {
        LocalDateTime now = LocalDateTime.now();
        mapper.reclaimStale(now.minus(properties.staleAfter()));
        int claimed = 0;
        for (LessonCleanupRow row : mapper.selectPending(now, properties.batchSize())) {
            if (mapper.claim(row.id()) == 1) {
                claimed++;
                try {
                    handle(row, now);
                } catch (RuntimeException e) {
                    // Keep the rest of the batch alive; the row stays RUNNING and the next tick
                    // reclaims it once stale-after elapses.
                    log.error("cleanup of {} failed unexpectedly", row.objectKey(), e);
                }
            }
        }
        return claimed;
    }

    private void handle(LessonCleanupRow row, LocalDateTime now) {
        if (isReferenced(row.objectKey())) {
            mapper.defer(row.id(), now.plus(properties.deferDelay()));
            return;
        }
        try {
            store.delete(row.objectKey());
            mapper.markDone(row.id());
            metrics.recordDeleted();
        } catch (LessonCleanupObjectStore.CleanupFailure failure) {
            fail(row, failure);
        }
    }

    private boolean isReferenced(String objectKey) {
        return mapper.countLessonReferences(objectKey) > 0
                || mapper.countPendingUploads(objectKey) > 0
                || mapper.countActiveTranscodes(objectKey) > 0;
    }

    private void fail(LessonCleanupRow row, LessonCleanupObjectStore.CleanupFailure failure) {
        int attempts = row.attemptCount() + 1;
        if (failure.permanent() || attempts >= properties.maxAttempts()) {
            mapper.markFailed(row.id(), attempts, failure.getMessage());
            metrics.recordFailed();
            log.warn("cleanup of {} failed after {} attempts", row.objectKey(), attempts);
            return;
        }
        mapper.scheduleRetry(
                row.id(),
                attempts,
                LocalDateTime.now().plus(backoff(attempts)),
                failure.getMessage());
        metrics.recordRetried();
    }

    private Duration backoff(int attempts) {
        if (attempts > 16) {
            return properties.maxBackoff();
        }
        Duration delay = properties.baseBackoff().multipliedBy(1L << (attempts - 1));
        return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
    }
}
