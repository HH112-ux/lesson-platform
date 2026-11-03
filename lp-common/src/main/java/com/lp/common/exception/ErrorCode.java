package com.lp.common.exception;

/**
 * Canonical error codes with segment semantics.
 *
 * <p>Segments follow {@code AGENTS.md} section 11.4:
 *
 * <ul>
 *   <li>{@code 4xxxx} client or protocol errors: 400 parameter, 401 credential, 403 permission, 404
 *       resource, 409 state, 429 rate limit
 *   <li>{@code 5xxxx} server or dependency errors: 500 uncaught, 502 third-party channel, 503 AI
 *       pipeline
 *   <li>{@code 6xxxx} business errors, second digit identifies the domain: 61 course, 62 trade, 63
 *       learning, 64 interaction, 65 AI session, 66 miniapp login
 * </ul>
 *
 * <p>The same semantic meaning must not be defined in two segments. Domain codes are appended per
 * milestone; string literals must never be used as error codes in business code.
 *
 * @author Caspian
 * @since 0.1.0
 */
public enum ErrorCode {

    /** Invalid or missing request parameters. */
    PARAM_INVALID(40001, "invalid request parameter"),

    /** Missing credentials or trusted identity. */
    UNAUTHENTICATED(40101, "missing credentials or identity"),

    /** Expired access token or session. */
    TOKEN_EXPIRED(40102, "credential expired"),

    /** Authenticated but not allowed to perform the action. */
    FORBIDDEN(40301, "access denied"),

    /** Requested resource does not exist. */
    NOT_FOUND(40401, "resource not found"),

    /** Resource is in a state that rejects the requested transition. */
    STATE_CONFLICT(40901, "state conflict"),

    /** Request rate exceeded the configured limit. */
    RATE_LIMITED(42901, "too many requests"),

    /** Uncaught server-side failure. */
    INTERNAL_ERROR(50001, "internal server error"),

    /** Third-party channel failure, for example a payment provider. */
    CHANNEL_UPSTREAM_ERROR(50201, "upstream channel error"),

    /** AI pipeline unavailable or not configured. */
    AI_PIPELINE_UNAVAILABLE(50301, "ai pipeline unavailable"),

    /** Course domain failure. */
    COURSE_BIZ_ERROR(61001, "course operation failed"),

    /** Trade domain failure. */
    TRADE_BIZ_ERROR(62001, "trade operation failed"),

    /** Payment channel does not match the request source platform. */
    TRADE_CHANNEL_SOURCE_MISMATCH(62005, "payment channel does not match request source"),

    /** Learning domain failure. */
    LEARN_BIZ_ERROR(63001, "learning operation failed"),

    /** Interaction domain failure. */
    INTERACTION_BIZ_ERROR(64001, "interaction operation failed"),

    /** AI session domain failure. */
    AI_SESSION_BIZ_ERROR(65001, "ai session operation failed"),

    /** Miniapp login failure. */
    MINIAPP_LOGIN_BIZ_ERROR(66001, "miniapp login failed"),

    /** Miniapp login is switched off on this deployment. */
    MINIAPP_LOGIN_DISABLED(66003, "miniapp login is disabled");

    private final int code;

    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Returns the numeric code exposed to clients.
     *
     * @return segmented numeric error code
     * @author Caspian
     * @since 0.1.0
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the default English message used when no custom message is supplied.
     *
     * @return default message text
     * @author Caspian
     * @since 0.1.0
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
