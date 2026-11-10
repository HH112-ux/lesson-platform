package com.lp.trade.dto;

/**
 * Request from auth-service to overwrite the payer identifiers of one deleted account.
 *
 * <p>The salt travels with the request because auth-service owns it: that service is the only
 * writer of cancellation decisions, and a second copy in the trade configuration would give
 * operations two secrets to rotate. The call is guarded by the internal token and the salt is never
 * logged.
 *
 * @param userId account owner
 * @param salt secret salt mixed into the digest
 * @author Caspian
 * @since 0.3.0
 */
public record AccountAnonymizationCommand(Long userId, String salt) {}
