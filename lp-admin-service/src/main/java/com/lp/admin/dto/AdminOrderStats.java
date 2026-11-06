package com.lp.admin.dto;

/**
 * Order counts returned by trade-service.
 *
 * @param totalOrders live orders
 * @param paidOrders settled orders
 * @param paidAmountFen settled revenue in fen
 * @author Caspian
 * @since 0.1.0
 */
public record AdminOrderStats(long totalOrders, long paidOrders, long paidAmountFen) {}
