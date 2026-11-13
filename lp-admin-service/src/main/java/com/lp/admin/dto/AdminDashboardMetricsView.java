package com.lp.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Graphical dashboard served to the operations console.
 *
 * <p>Every figure is aggregated from the domain services; nothing is hard coded. The window is the
 * trailing {@code days} calendar days ending today. Revenue counts settled orders created inside
 * the window, so refunded orders never inflate it. The refund backlog counters ignore the window on
 * purpose: they describe what operations must clear now.
 *
 * <p>{@code revenueFen} and {@code netRevenueFen} are independent and must be presented side by
 * side. {@code revenueFen} is the gross merchandise volume attributed to the order creation day and
 * is never netted out. {@code netRevenueFen} is that gross value minus refunds settled inside the
 * window, attributed to the refund settlement day, so the two figures may disagree and the net
 * value may even be negative.
 *
 * @param days effective window length after clamping
 * @param revenueFen gross merchandise volume in fen inside the window, attributed to the order
 *     creation day
 * @param netRevenueFen {@code revenueFen} minus refunds settled inside the window, in fen
 * @param orderCount orders created inside the window
 * @param paidOrderCount settled orders created inside the window
 * @param paidRate {@code paidOrderCount / orderCount}, {@code 0} when nothing was created
 * @param studentTotal live accounts holding the {@code STUDENT} role
 * @param newStudents learner accounts registered inside the window
 * @param courseTotal live courses
 * @param coursePublished published courses
 * @param coursePendingReview courses waiting for review
 * @param refundAppliedCount refund applications created inside the window
 * @param refundedCount refunds settled inside the window
 * @param refundAmountFen refunded amount inside the window, in fen
 * @param refundPendingCount refunds waiting for a review decision
 * @param refundProcessingCount refunds approved but not settled locally
 * @param dailyTrend one entry per calendar day, oldest first, zero filled
 * @param courseSales best selling courses of the window, highest first
 * @author Caspian
 * @since 0.21.0
 */
public record AdminDashboardMetricsView(
        int days,
        long revenueFen,
        long netRevenueFen,
        long orderCount,
        long paidOrderCount,
        BigDecimal paidRate,
        long studentTotal,
        long newStudents,
        long courseTotal,
        long coursePublished,
        long coursePendingReview,
        long refundAppliedCount,
        long refundedCount,
        long refundAmountFen,
        long refundPendingCount,
        long refundProcessingCount,
        List<AdminDailyMetricView> dailyTrend,
        List<AdminCourseSalesRankView> courseSales) {}
