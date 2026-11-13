package com.lp.admin.dto;

/**
 * One calendar day of the dashboard trend returned by trade-service.
 *
 * @param date day in {@code yyyy-MM-dd} form
 * @param orderCount orders created on the day
 * @param paidOrderCount settled orders created on the day
 * @param revenueFen settled revenue of the day in fen
 * @author Caspian
 * @since 0.21.0
 */
public record AdminDailyMetricView(
        String date, long orderCount, long paidOrderCount, long revenueFen) {}
