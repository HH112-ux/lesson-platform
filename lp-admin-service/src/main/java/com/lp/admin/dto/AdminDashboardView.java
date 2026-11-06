package com.lp.admin.dto;

/**
 * Aggregated operations dashboard figures.
 *
 * <p>Amounts are in fen. Trend series are intentionally omitted until the reporting tables exist;
 * the console only needs the current totals to be useful.
 *
 * @param courseTotal live courses
 * @param coursePendingReview courses waiting for review
 * @param coursePublished published courses
 * @param orderTotal live orders
 * @param paidOrderCount settled orders
 * @param paidAmountFen settled revenue in fen
 * @param userTotal live accounts
 * @author Caspian
 * @since 0.1.0
 */
public record AdminDashboardView(
        long courseTotal,
        long coursePendingReview,
        long coursePublished,
        long orderTotal,
        long paidOrderCount,
        long paidAmountFen,
        long userTotal) {}
