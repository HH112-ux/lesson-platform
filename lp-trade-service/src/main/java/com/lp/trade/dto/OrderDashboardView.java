package com.lp.trade.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order and refund figures of the operations dashboard window.
 *
 * <p>The window is exactly {@code days} calendar days ending today in the server time zone. Revenue
 * counts settled orders only, so refunded orders (status {@code 5}) never inflate it. Refund
 * counters mix two windows on purpose: applications and settled refunds are limited to the window,
 * while the pending and processing counters describe the current backlog that operations must clear
 * today.
 *
 * <p>{@code gmvFen} and {@code netRevenueFen} answer different questions and must not replace each
 * other. {@code gmvFen} attributes settled orders to the day they were created and does not net
 * refunds out. {@code netRevenueFen} subtracts refunds settled inside the window, attributed to
 * their {@code refunded_at} day, from that gross figure. Refunds of orders created before the
 * window therefore lower the net figure without lowering the gross figure, so a net value can be
 * negative when the window settles more refunds than new orders.
 *
 * @param days effective window length after clamping
 * @param gmvFen settled revenue in fen inside the window, attributed to the order creation day
 * @param netRevenueFen {@code gmvFen} minus refunds settled inside the window, in fen
 * @param orderCount orders created inside the window
 * @param paidOrderCount settled orders created inside the window
 * @param paidRate {@code paidOrderCount / orderCount}, {@code 0} when nothing was created
 * @param refundAppliedCount refund applications created inside the window
 * @param refundedCount refunds settled inside the window
 * @param refundAmountFen refunded amount inside the window, in fen
 * @param refundPendingCount refunds waiting for a review decision, current backlog
 * @param refundProcessingCount refunds approved but not settled locally, current backlog
 * @param dailyTrend one entry per calendar day of the window, oldest first, zero filled
 * @param courseSales best selling courses of the window, highest first
 * @author Caspian
 * @since 0.21.0
 */
public record OrderDashboardView(
        int days,
        long gmvFen,
        long netRevenueFen,
        long orderCount,
        long paidOrderCount,
        BigDecimal paidRate,
        long refundAppliedCount,
        long refundedCount,
        long refundAmountFen,
        long refundPendingCount,
        long refundProcessingCount,
        List<DailyOrderMetricView> dailyTrend,
        List<CourseSalesRankView> courseSales) {}
