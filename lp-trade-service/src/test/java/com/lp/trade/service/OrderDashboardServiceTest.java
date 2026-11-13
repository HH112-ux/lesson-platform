package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.lp.trade.dto.OrderDashboardView;
import com.lp.trade.entity.CourseSalesRankRow;
import com.lp.trade.entity.DailyOrderMetricRow;
import com.lp.trade.entity.RefundMetricsRow;
import com.lp.trade.mapper.RefundRequestMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the dashboard aggregation.
 *
 * @author Caspian
 * @since 0.21.0
 */
@ExtendWith(MockitoExtension.class)
class OrderDashboardServiceTest {

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private RefundRequestMapper refundRequestMapper;

    @InjectMocks private OrderDashboardService service;

    /**
     * A window is zero filled to one point per day and the totals are summed from the buckets.
     *
     * @author Caspian
     * @since 0.21.0
     */
    @Test
    void zeroFillsTrendAndAggregatesTotals() {
        LocalDate today = LocalDate.now();
        DailyOrderMetricRow todayRow = row(today, 3, 2, "199.00");
        DailyOrderMetricRow olderRow = row(today.minusDays(2), 1, 1, "99.50");
        when(tradeOrderMapper.selectDailyMetrics(any(), any()))
                .thenReturn(List.of(todayRow, olderRow));
        when(refundRequestMapper.selectMetrics(any(), any())).thenReturn(refunds());
        when(tradeOrderMapper.selectCourseSalesRank(any(), any(), anyInt()))
                .thenReturn(List.of(courseRow()));

        OrderDashboardView view = service.dashboard(30);

        assertEquals(30, view.days());
        assertEquals(30, view.dailyTrend().size());
        assertEquals(today.minusDays(29).toString(), view.dailyTrend().get(0).date());
        assertEquals(today.toString(), view.dailyTrend().get(29).date());
        assertEquals(0, view.dailyTrend().get(0).orderCount());
        assertEquals(29850L, view.gmvFen());
        assertEquals(23850L, view.netRevenueFen());
        assertEquals(4, view.orderCount());
        assertEquals(3, view.paidOrderCount());
        assertEquals(new BigDecimal("0.7500"), view.paidRate());
        assertEquals(4, view.refundAppliedCount());
        assertEquals(2, view.refundedCount());
        assertEquals(6000L, view.refundAmountFen());
        assertEquals(1, view.refundPendingCount());
        assertEquals(1, view.refundProcessingCount());
        assertEquals(1, view.courseSales().size());
        assertEquals("Java basics", view.courseSales().get(0).title());
    }

    /**
     * The window is clamped to the accepted range.
     *
     * @author Caspian
     * @since 0.21.0
     */
    @Test
    void clampsWindowLength() {
        when(tradeOrderMapper.selectDailyMetrics(any(), any())).thenReturn(List.of());
        when(refundRequestMapper.selectMetrics(any(), any())).thenReturn(new RefundMetricsRow());
        when(tradeOrderMapper.selectCourseSalesRank(any(), any(), anyInt())).thenReturn(List.of());

        assertEquals(1, service.dashboard(0).days());
        assertEquals(90, service.dashboard(365).days());
    }

    private static DailyOrderMetricRow row(LocalDate day, long orders, long paid, String revenue) {
        DailyOrderMetricRow row = new DailyOrderMetricRow();
        row.setDay(day);
        row.setOrderCount(orders);
        row.setPaidOrderCount(paid);
        row.setRevenue(new BigDecimal(revenue));
        return row;
    }

    private static RefundMetricsRow refunds() {
        RefundMetricsRow row = new RefundMetricsRow();
        row.setAppliedCount(4L);
        row.setRefundedCount(2L);
        row.setRefundedAmount(new BigDecimal("60.00"));
        row.setPendingCount(1L);
        row.setProcessingCount(1L);
        return row;
    }

    private static CourseSalesRankRow courseRow() {
        CourseSalesRankRow row = new CourseSalesRankRow();
        row.setCourseId(7L);
        row.setTitle("Java basics");
        row.setOrderCount(2L);
        row.setRevenue(new BigDecimal("199.00"));
        return row;
    }
}
