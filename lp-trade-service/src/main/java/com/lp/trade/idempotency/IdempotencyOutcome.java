package com.lp.trade.idempotency;

/**
 * Result of an idempotent write together with its replay flag.
 *
 * @param value response payload, either freshly produced or replayed
 * @param replayed true when no business logic ran and the first result was returned
 * @param <T> payload type
 * @author Caspian
 * @since 0.4.0
 */
public record IdempotencyOutcome<T>(T value, boolean replayed) {}
