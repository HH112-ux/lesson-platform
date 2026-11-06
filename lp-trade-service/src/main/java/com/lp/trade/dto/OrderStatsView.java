package com.lp.trade.dto;

/**
 * Order counts and revenue used by the operations dashboard.
 *
 * @param totalOrders all live orders
 * @param paidOrders orders settled in status {@code 1}
 * @param paidAmountFen sum of {@code pay_amount} of paid orders, in fen
 * @author Caspian
 * @since 0.1.0
 */
public record OrderStatsView(long totalOrders, long paidOrders, long paidAmountFen) {}
