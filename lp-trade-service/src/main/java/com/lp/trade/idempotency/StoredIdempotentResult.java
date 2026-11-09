package com.lp.trade.idempotency;

/**
 * Cached first result of an idempotent write.
 *
 * @param digest request digest the key was first used with
 * @param payload serialized successful response body
 * @author Caspian
 * @since 0.4.0
 */
public record StoredIdempotentResult(String digest, String payload) {}
