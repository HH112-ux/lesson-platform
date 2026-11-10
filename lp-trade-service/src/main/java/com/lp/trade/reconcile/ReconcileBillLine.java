package com.lp.trade.reconcile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of an imported channel bill.
 *
 * @param tradeNo channel transaction number: Alipay {@code trade_no} for payments, the channel
 *     refund number for refunds
 * @param merchantOrderNo platform number for traceability: {@code pay_no} for payments, {@code
 *     refund_no} for refunds; optional
 * @param amount amount in yuan with at most two decimals, always positive
 * @param occurredAt channel occurrence time in Asia/Shanghai; optional
 * @author Caspian
 * @since 0.6.1
 */
public record ReconcileBillLine(
        String tradeNo, String merchantOrderNo, BigDecimal amount, LocalDateTime occurredAt) {}
