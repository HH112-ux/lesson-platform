package com.lp.auth.dto;

/**
 * Account cancellation state returned by the apply, query and duplicate-apply paths.
 *
 * <p>{@code effectiveAt} is the deadline fixed when the request was created and {@code
 * remainingSeconds} lets a client render a countdown without trusting its own clock. A user who
 * never applied receives {@code null} status and deadline with zero remaining seconds.
 *
 * @param status request status: 0 cooling off, 1 withdrawn, 2 executed, {@code null} when absent
 * @param effectiveAt ISO-8601 local deletion deadline, or {@code null} when absent
 * @param remainingSeconds seconds left before the deadline, zero when not cooling off
 * @author Caspian
 * @since 0.2.0
 */
public record AccountCancellationView(Integer status, String effectiveAt, long remainingSeconds) {}
