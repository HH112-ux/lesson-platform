package com.lp.auth.constant;

/**
 * Status codes of {@code sys_user.status}.
 *
 * <p>Only {@link #ACTIVE} and {@link #COOLING_OFF} can log in. A cancelled account answers {@code
 * 67001} so a user can never mistake the rejection for a ban, while a disabled account keeps the
 * generic {@code 40301}.
 *
 * @author Caspian
 * @since 0.2.0
 */
public final class UserStatus {

    /**
     * Account is disabled or banned and cannot log in.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final int DISABLED = 0;

    /**
     * Account is usable and every feature is available.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final int ACTIVE = 1;

    /**
     * Account is inside the cancellation cooling-off period: sign-in and existing purchases stay
     * available, while new orders and payments are refused.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final int COOLING_OFF = 2;

    /**
     * Account cancellation has been executed and the row is anonymized.
     *
     * @author Caspian
     * @since 0.2.0
     */
    public static final int CANCELLED = 3;

    /**
     * Prevents instantiation of this constant holder.
     *
     * @author Caspian
     * @since 0.2.0
     */
    private UserStatus() {}
}
