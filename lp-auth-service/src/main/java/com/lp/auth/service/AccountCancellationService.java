package com.lp.auth.service;

import com.lp.auth.dto.AccountCancellationView;

/**
 * Account cancellation requests made by the account owner.
 *
 * <p>Applying starts the cooling-off period and fixes the deletion deadline; withdrawing cancels it
 * before the deadline; querying reports the last outcome so a client can render a countdown.
 *
 * @author Caspian
 * @since 0.2.0
 */
public interface AccountCancellationService {

    /**
     * Applies for cancellation or returns the request already in progress.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @param source trigger source recorded in logs, {@code user} for this entry point
     * @return the deadline and status of the request, identical to the existing one on replay
     * @author Caspian
     * @since 0.2.0
     */
    AccountCancellationView apply(long userId, String source);

    /**
     * Withdraws the request currently in the cooling-off period.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @param source trigger source recorded in logs, {@code user} for this entry point
     * @author Caspian
     * @since 0.2.0
     */
    void withdraw(long userId, String source);

    /**
     * Reports the most recent cancellation state of a user.
     *
     * @param userId authentic user id resolved from the trusted gateway header
     * @return status, deadline and remaining seconds; all empty values when never applied
     * @author Caspian
     * @since 0.2.0
     */
    AccountCancellationView get(long userId);
}
