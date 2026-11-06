package com.lp.trade.service;

import com.lp.trade.dto.AdminOrderPageView;
import com.lp.trade.dto.AdminOrderView;
import com.lp.trade.dto.CourseSaleView;
import com.lp.trade.dto.OrderMetricsView;
import com.lp.trade.entity.AdminOrderRow;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only order queries for the operations console and the dashboard.
 *
 * <p>Dates are converted to a half open {@code [from, to)} instant range so a range like {@code
 * 2026-01-01..2026-01-01} still includes every order of that single day.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
public class OrderAdminQueryService {

    /** Maximum page size accepted from the console. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Number of rows in the best selling course ranking. */
    private static final int TOP_COURSE_LIMIT = 5;

    /** Maximum dashboard window in days. */
    private static final int MAX_METRIC_DAYS = 90;

    private final TradeOrderMapper tradeOrderMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param tradeOrderMapper order statements
     * @author Caspian
     * @since 0.2.0
     */
    public OrderAdminQueryService(TradeOrderMapper tradeOrderMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
    }

    /**
     * Lists one page of orders, newest first.
     *
     * @param status optional order status
     * @param channel optional payment channel
     * @param orderNo optional order number fragment
     * @param userId optional buyer id
     * @param dateFrom optional inclusive creation date
     * @param dateTo optional inclusive creation date upper bound
     * @param pageNum one based page number
     * @param pageSize page size, clamped to {@code 1..100}
     * @return the page together with the total match count
     * @author Caspian
     * @since 0.2.0
     */
    public AdminOrderPageView page(
            Integer status,
            Integer channel,
            String orderNo,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int pageNum,
            int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String normalizedOrderNo = orderNo == null ? null : orderNo.trim();
        LocalDateTime from = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime to = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();
        long total =
                tradeOrderMapper.countForAdmin(
                        status, channel, normalizedOrderNo, userId, from, to);
        if (total == 0) {
            return new AdminOrderPageView(List.of(), 0L, safePage, safeSize);
        }
        List<AdminOrderRow> rows =
                tradeOrderMapper.selectPageForAdmin(
                        status,
                        channel,
                        normalizedOrderNo,
                        userId,
                        from,
                        to,
                        (long) (safePage - 1) * safeSize,
                        safeSize);
        List<AdminOrderView> records =
                rows.stream()
                        .map(
                                row ->
                                        new AdminOrderView(
                                                row.getOrderNo(),
                                                row.getUserId(),
                                                row.getStatus(),
                                                row.getPayAmount(),
                                                row.getCreatedAt()))
                        .toList();
        return new AdminOrderPageView(records, total, safePage, safeSize);
    }

    /**
     * Aggregates the dashboard order metrics over the trailing window.
     *
     * @param days window length in days, clamped to {@code 1..90}
     * @return revenue, order counts, paid conversion and the best selling courses
     * @author Caspian
     * @since 0.2.0
     */
    public OrderMetricsView metrics(int days) {
        int safeDays = Math.min(Math.max(days, 1), MAX_METRIC_DAYS);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(safeDays);
        BigDecimal gmv = tradeOrderMapper.sumPaidBetween(from, to);
        long orderCount = tradeOrderMapper.countCreatedBetween(from, to);
        long paidOrderCount = tradeOrderMapper.countPaidBetween(from, to);
        BigDecimal paidRate = BigDecimal.ZERO.setScale(4);
        if (orderCount > 0) {
            paidRate =
                    BigDecimal.valueOf(paidOrderCount)
                            .divide(BigDecimal.valueOf(orderCount), 4, RoundingMode.HALF_UP);
        }
        List<CourseSaleView> top5 =
                tradeOrderMapper.selectTopCourses(from, to, TOP_COURSE_LIMIT).stream()
                        .map(
                                row ->
                                        new CourseSaleView(
                                                row.getCourseId(),
                                                row.getTitle(),
                                                row.getOrderCount() == null
                                                        ? 0L
                                                        : row.getOrderCount()))
                        .toList();
        return new OrderMetricsView(
                gmv == null ? new BigDecimal("0.00") : gmv,
                orderCount,
                paidOrderCount,
                paidRate,
                top5);
    }
}
