package com.lp.auth.service;

import com.lp.auth.dto.AccountCancellationView;
import com.lp.auth.dto.AccountDeletionStateView;

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

    /**
     * Reports whether a user is inside the cooling-off period for other services.
     *
     * <p>Only the active request row decides this, so the answer stays correct even when the
     * deadline has passed but the execution job has not run yet; refusing a purchase longer than
     * promised is the safe direction.
     *
     * @param userId account owner
     * @return cooling-off decision plus the deadline when one is active
     * @author Caspian
     * @since 0.3.0
     */
    AccountDeletionStateView state(long userId);
}
