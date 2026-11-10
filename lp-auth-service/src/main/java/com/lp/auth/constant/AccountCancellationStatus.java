package com.lp.auth.constant;

/**
 * Status codes of {@code user_account_cancellation.status}.
 *
 * <p>The active status drives the generated unique key, so only one cooling-off request can exist
 * per user at a time.
 *
 * @author Caspian
 * @since 0.2.0
 */
public final class AccountCancellationStatus {

    /**
     * Request is inside the cooling-off period and can still be withdrawn.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final int COOLING_OFF = 0;

    /**
     * Request was withdrawn by the user before it took effect.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final int WITHDRAWN = 1;

    /**
     * Request was executed after the cooling-off period ended.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final int EXECUTED = 2;

    /**
     * Prevents instantiation of this constant holder.
     *
     * @author Caspian
     * @since 0.2.0
     */
    private AccountCancellationStatus() {}
}
