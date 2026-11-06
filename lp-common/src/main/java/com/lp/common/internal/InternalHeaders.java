package com.lp.common.internal;

/**
 * Header names used by service-to-service calls under {@code /internal/v1/**}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class InternalHeaders {

    /** Shared secret that proves the caller is an internal service, not a public client. */
    public static final String INTERNAL_TOKEN = "X-Internal-Token";

    private InternalHeaders() {}
}
