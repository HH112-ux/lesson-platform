package com.lp.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order and refund figures returned by trade-service for the dashboard.
 *
 * <p>{@code gmvFen} and {@code netRevenueFen} are independent figures with different time anchors.
 * The gross value attributes settled orders to their creation day and never nets refunds out. The
 * net value subtracts refunds settled inside the window from the gross value, so it can be lower
 * than the gross value, or negative when old orders are refunded faster than new orders settle.
 *
 * @param days effective window length after clamping
 * @param gmvFen settled revenue in fen inside the window, attributed to the order creation day
 * @param netRevenueFen {@code gmvFen} minus refunds settled inside the window, in fen
 * @param orderCount orders created inside the window
 * @param paidOrderCount settled orders created inside the window
 * @param paidRate paid conversion of the window
 * @param refundAppliedCount refund applications created inside the window
 * @param refundedCount refunds settled inside the window
 * @param refundAmountFen refunded amount inside the window, in fen
 * @param refundPendingCount refunds waiting for a review decision
 * @param refundProcessingCount refunds approved but not settled locally
 * @param dailyTrend one entry per calendar day, oldest first
 * @param courseSales best selling courses of the window, highest first
 * @author Caspian
 * @since 0.21.0
 */
public record AdminOrderDashboard(
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
        List<AdminDailyMetricView> dailyTrend,
        List<AdminCourseSalesRankView> courseSales) {}
