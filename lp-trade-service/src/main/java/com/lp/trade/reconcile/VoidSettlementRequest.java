package com.lp.trade.reconcile;

/**
 * Request body that voids one closed settle batch and unlocks its bill day.
 *
 * @param reason why the batch must be voided, required and at most 300 characters
 * @author Caspian
 * @since 0.6.2
 */
public record VoidSettlementRequest(String reason) {}
