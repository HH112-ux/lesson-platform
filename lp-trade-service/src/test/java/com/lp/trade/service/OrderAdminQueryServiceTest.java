package com.lp.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.trade.dto.AdminOrderPageView;
import com.lp.trade.dto.OrderMetricsView;
import com.lp.trade.entity.AdminOrderRow;
import com.lp.trade.entity.CourseSaleRow;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the console order list.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
class OrderAdminQueryServiceTest {

    @Mock private TradeOrderMapper tradeOrderMapper;

    @InjectMocks private OrderAdminQueryService orderAdminQueryService;

    /**
     * The inclusive date range becomes a half open instant range and rows are mapped.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void mapsPageAndConvertsDates() {
        AdminOrderRow row = new AdminOrderRow();
        row.setOrderNo("[ORDER_NO_1]");
        row.setUserId(3L);
        row.setStatus(1);
        row.setPayAmount(new BigDecimal("99.00"));
        row.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        when(tradeOrderMapper.countForAdmin(1, 2, "LP", 3L, from(), to())).thenReturn(1L);
        when(tradeOrderMapper.selectPageForAdmin(1, 2, "LP", 3L, from(), to(), 0L, 20L))
                .thenReturn(List.of(row));

        AdminOrderPageView page =
                orderAdminQueryService.page(
                        1,
                        2,
                        " LP ",
                        3L,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1),
                        1,
                        20);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).orderNo()).isEqualTo("[ORDER_NO_1]");
        assertThat(page.records().get(0).payAmount()).isEqualByComparingTo("99.00");
    }

    /**
     * An empty match skips the page query.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void skipsPageQueryWhenNothingMatches() {
        when(tradeOrderMapper.countForAdmin(null, null, null, null, null, null)).thenReturn(0L);

        AdminOrderPageView page =
                orderAdminQueryService.page(null, null, null, null, null, null, 1, 20);

        assertThat(page.records()).isEmpty();
        verify(tradeOrderMapper, never())
                .selectPageForAdmin(any(), any(), any(), any(), any(), any(), anyLong(), anyLong());
    }

    /**
     * Metrics convert counts to a rate, keep revenue and rank the courses.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void computesMetrics() {
        CourseSaleRow sale = new CourseSaleRow();
        sale.setCourseId(11L);
        sale.setTitle("Java Basics");
        sale.setOrderCount(4L);
        when(tradeOrderMapper.sumPaidBetween(any(), any())).thenReturn(new BigDecimal("200.00"));
        when(tradeOrderMapper.countCreatedBetween(any(), any())).thenReturn(4L);
        when(tradeOrderMapper.countPaidBetween(any(), any())).thenReturn(1L);
        when(tradeOrderMapper.selectTopCourses(any(), any(), anyInt())).thenReturn(List.of(sale));

        OrderMetricsView metrics = orderAdminQueryService.metrics(7);

        assertThat(metrics.gmv()).isEqualByComparingTo("200.00");
        assertThat(metrics.orderCount()).isEqualTo(4L);
        assertThat(metrics.paidOrderCount()).isEqualTo(1L);
        assertThat(metrics.paidRate()).isEqualByComparingTo("0.2500");
        assertThat(metrics.courseSalesTop5()).hasSize(1);
        assertThat(metrics.courseSalesTop5().get(0).title()).isEqualTo("Java Basics");
    }

    /**
     * An empty window reports a zero rate and zero revenue instead of failing.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void treatsEmptyWindowAsZero() {
        when(tradeOrderMapper.sumPaidBetween(any(), any())).thenReturn(null);
        when(tradeOrderMapper.countCreatedBetween(any(), any())).thenReturn(0L);
        when(tradeOrderMapper.countPaidBetween(any(), any())).thenReturn(0L);
        when(tradeOrderMapper.selectTopCourses(any(), any(), anyInt())).thenReturn(List.of());

        OrderMetricsView metrics = orderAdminQueryService.metrics(0);

        assertThat(metrics.gmv()).isEqualByComparingTo("0.00");
        assertThat(metrics.paidRate()).isEqualByComparingTo("0");
        assertThat(metrics.courseSalesTop5()).isEmpty();
    }

    private static LocalDateTime from() {
        return LocalDate.of(2026, 1, 1).atStartOfDay();
    }

    private static LocalDateTime to() {
        return LocalDate.of(2026, 1, 2).atStartOfDay();
    }
}
