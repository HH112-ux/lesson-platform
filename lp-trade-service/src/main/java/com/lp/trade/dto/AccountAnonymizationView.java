package com.lp.trade.dto;

/**
 * Outcome of anonymising the trade-side payer identifiers of one account.
 *
 * @param userId account owner
 * @param payerOpenidRewritten payment rows rewritten by this call, {@code 0} on a repeated call
 * @author Caspian
 * @since 0.3.0
 */
public record AccountAnonymizationView(long userId, int payerOpenidRewritten) {}
