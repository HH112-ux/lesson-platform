package com.lp.course.cleanup;

/**
 * Lifecycle of one row in {@code lesson_content_cleanup}.
 *
 * <p>The values match the column comment of the V11 migration, so no schema change was needed to
 * introduce the worker. A row only leaves the table through a terminal status: nothing deletes
 * rows, and a {@link #FAILED} row waits for a human instead of retrying forever.
 *
 * @author Caspian
 * @since 0.17.0
 */
public final class LessonCleanupStatus {

    /**
     * Waiting for its turn; the only status the worker picks up.
     *
     * <p>Leaves to {@link #RUNNING} on a successful claim. {@code next_retry_at} holds either the
     * backoff of a transient failure or the delay of a still-referenced object; in both cases the
     * row comes back here and never becomes terminal on its own.
     */
    public static final int PENDING = 0;

    /**
     * Claimed by one worker tick; not visible to other ticks.
     *
     * <p>Must leave in the same tick. A row still here after {@code staleAfter} means the process
     * died mid-flight, and the next tick returns it to {@link #PENDING} with one more attempt.
     */
    public static final int RUNNING = 1;

    /** Terminal success: the object is gone, or it was already gone. */
    public static final int DONE = 2;

    /**
     * Terminal failure that needs a human.
     *
     * <p>Reached either on a permanent error or after {@code maxAttempts} transient failures.
     * Nothing retries it; to retry again, set the row back to {@link #PENDING} by hand.
     */
    public static final int FAILED = 3;

    private LessonCleanupStatus() {}
}
