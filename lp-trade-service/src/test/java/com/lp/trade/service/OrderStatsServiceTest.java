package com.lp.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.lp.trade.dto.OrderStatsView;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the order aggregates used by the operations dashboard.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class OrderStatsServiceTest {

    @Mock private TradeOrderMapper tradeOrderMapper;

    @InjectMocks private OrderStatsService orderStatsService;

    /**
     * Paid revenue stored in yuan is reported in fen.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void convertsPaidAmountToFen() {
        when(tradeOrderMapper.countAll()).thenReturn(12L);
        when(tradeOrderMapper.countPaid()).thenReturn(5L);
        when(tradeOrderMapper.sumPaidAmount()).thenReturn(new BigDecimal("199.50"));

        OrderStatsView stats = orderStatsService.stats();

        assertEquals(12L, stats.totalOrders());
        assertEquals(5L, stats.paidOrders());
        assertEquals(19950L, stats.paidAmountFen());
    }

    /**
     * An empty order table yields zero rather than failing on a {@code null} sum.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void treatsMissingSumAsZero() {
        when(tradeOrderMapper.countAll()).thenReturn(0L);
        when(tradeOrderMapper.countPaid()).thenReturn(0L);
        when(tradeOrderMapper.sumPaidAmount()).thenReturn(null);

        OrderStatsView stats = orderStatsService.stats();

        assertEquals(0L, stats.paidAmountFen());
    }

    /**
     * A course counts as sold only while an order still grants it.
     *
     * @author Caspian
     * @since 0.15.0
     */
    @Test
    void reportsWhetherCourseHasPaidOrders() {
        when(tradeOrderMapper.countPaidByCourse(9L)).thenReturn(2L, 0L);

        assertTrue(orderStatsService.hasPaidOrder(9L));
        assertFalse(orderStatsService.hasPaidOrder(9L));
    }
}
