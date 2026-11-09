package com.lp.trade.idempotency;

/**
 * HTTP header names of the idempotent write contract.
 *
 * <p>The request header follows the IETF idempotency-key draft, so it carries no {@code X-} prefix.
 * The response header is informational only and must not be used for control flow.
 *
 * @author Caspian
 * @since 0.4.0
 */
public final class IdempotencyHeaders {

    /** Request header carrying the client generated idempotency key. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Response header set to {@code true} when the body was replayed from the first result. */
    public static final String REPLAYED = "X-Idempotent-Replayed";

    private IdempotencyHeaders() {}
}
