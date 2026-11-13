package com.lp.trade.service;

import com.lp.common.money.Money;
import com.lp.trade.dto.CourseSalesRankView;
import com.lp.trade.dto.DailyOrderMetricView;
import com.lp.trade.dto.OrderDashboardView;
import com.lp.trade.entity.DailyOrderMetricRow;
import com.lp.trade.entity.RefundMetricsRow;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Read-only dashboard aggregation served to the operations console.
 *
 * <p>The window is exactly {@code days} calendar days ending today in the server time zone, so the
 * chart always shows one bar per day even when nothing happened. Revenue and order counts reuse the
 * same definition as the seven day overview: settled orders created inside the window, which keeps
 * recalled refunds out of the figure.
 *
 * <p>The service returns both the gross and the net figure. The gross {@code gmvFen} stays on the
 * order creation day and is never netted; {@code netRevenueFen} subtracts refunds settled inside
 * the window, so the two figures intentionally use different time anchors and must be shown
 * separately.
 *
 * @author Caspian
 * @since 0.21.0
 */
@Service
public class OrderDashboardService {

    /** Smallest accepted window. */
    private static final int MIN_DAYS = 1;

    /** Largest accepted window; the console only asks for thirty days. */
    private static final int MAX_DAYS = 90;

    /** Number of rows in the dashboard course ranking. */
    private static final int TOP_COURSE_LIMIT = 10;

    private final TradeOrderMapper tradeOrderMapper;

    private final RefundRequestMapper refundRequestMapper;

    /**
     * Creates the service with its mapper dependencies.
     *
     * @param tradeOrderMapper order statements
     * @param refundRequestMapper refund statements
     * @author Caspian
     * @since 0.21.0
     */
    public OrderDashboardService(
            TradeOrderMapper tradeOrderMapper, RefundRequestMapper refundRequestMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.refundRequestMapper = refundRequestMapper;
    }

    /**
     * Aggregates the dashboard figures of the trailing calendar window.
     *
     * @param days window length in days, clamped to {@code 1..90}
     * @return order, refund, daily trend and course ranking figures
     * @author Caspian
     * @since 0.21.0
     */
    public OrderDashboardView dashboard(int days) {
        int safeDays = Math.min(Math.max(days, MIN_DAYS), MAX_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(safeDays - 1L);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        Map<LocalDate, DailyOrderMetricRow> byDay = new HashMap<>();
        for (DailyOrderMetricRow row : tradeOrderMapper.selectDailyMetrics(from, to)) {
            if (row.getDay() != null) {
                byDay.put(row.getDay(), row);
            }
        }

        List<DailyOrderMetricView> trend = new ArrayList<>(safeDays);
        long orderCount = 0;
        long paidOrderCount = 0;
        long revenueFen = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            DailyOrderMetricRow row = byDay.get(day);
            long dayOrders = row == null ? 0 : nullToZero(row.getOrderCount());
            long dayPaid = row == null ? 0 : nullToZero(row.getPaidOrderCount());
            long dayRevenue = row == null ? 0 : Money.toFen(row.getRevenue());
            orderCount += dayOrders;
            paidOrderCount += dayPaid;
            revenueFen += dayRevenue;
            trend.add(new DailyOrderMetricView(day.toString(), dayOrders, dayPaid, dayRevenue));
        }

        BigDecimal paidRate = BigDecimal.ZERO.setScale(4);
        if (orderCount > 0) {
            paidRate =
                    BigDecimal.valueOf(paidOrderCount)
                            .divide(BigDecimal.valueOf(orderCount), 4, RoundingMode.HALF_UP);
        }

        RefundMetricsRow refunds = refundRequestMapper.selectMetrics(from, to);
        long refundAppliedCount = refunds == null ? 0 : nullToZero(refunds.getAppliedCount());
        long refundedCount = refunds == null ? 0 : nullToZero(refunds.getRefundedCount());
        long refundAmountFen = refunds == null ? 0 : Money.toFen(refunds.getRefundedAmount());
        long refundPendingCount = refunds == null ? 0 : nullToZero(refunds.getPendingCount());
        long refundProcessingCount = refunds == null ? 0 : nullToZero(refunds.getProcessingCount());
        long netRevenueFen = revenueFen - refundAmountFen;

        List<CourseSalesRankView> courseSales =
                tradeOrderMapper.selectCourseSalesRank(from, to, TOP_COURSE_LIMIT).stream()
                        .map(
                                row ->
                                        new CourseSalesRankView(
                                                row.getCourseId() == null ? 0L : row.getCourseId(),
                                                row.getTitle(),
                                                nullToZero(row.getOrderCount()),
                                                Money.toFen(row.getRevenue())))
                        .toList();

        return new OrderDashboardView(
                safeDays,
                revenueFen,
                netRevenueFen,
                orderCount,
                paidOrderCount,
                paidRate,
                refundAppliedCount,
                refundedCount,
                refundAmountFen,
                refundPendingCount,
                refundProcessingCount,
                trend,
                courseSales);
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
