package com.lp.trade.reconcile;

import java.math.BigDecimal;

/**
 * One local payment or refund reduced to the fields needed for reconciliation.
 *
 * @param id primary key of {@code payment_record} or {@code refund_request}
 * @param tradeNo channel trade number stored on the local row
 * @param amount amount in yuan
 * @author Caspian
 * @since 0.6.1
 */
public record LocalReconcileRecord(Long id, String tradeNo, BigDecimal amount) {}
