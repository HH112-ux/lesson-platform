package com.lp.trade.dto;

/**
 * One calendar day of the dashboard trend.
 *
 * @param date day in {@code yyyy-MM-dd} form, server local time zone
 * @param orderCount orders created on the day
 * @param paidOrderCount orders created on the day that are settled in status {@code 1}
 * @param revenueFen settled revenue of the day in fen
 * @author Caspian
 * @since 0.21.0
 */
public record DailyOrderMetricView(
        String date, long orderCount, long paidOrderCount, long revenueFen) {}
