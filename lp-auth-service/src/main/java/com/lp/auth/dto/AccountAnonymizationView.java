package com.lp.auth.dto;

/**
 * Trade-side outcome of anonymising one account's payer identifiers.
 *
 * @param userId account owner
 * @param payerOpenidRewritten payment rows rewritten by the call, {@code 0} on a retry
 * @author Caspian
 * @since 0.3.0
 */
public record AccountAnonymizationView(long userId, int payerOpenidRewritten) {}
