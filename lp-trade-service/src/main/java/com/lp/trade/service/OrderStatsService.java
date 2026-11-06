package com.lp.trade.service;

import com.lp.trade.dto.OrderStatsView;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Read-only order aggregates for the operations dashboard.
 *
 * <p>Revenue counts settled orders only: refunding orders still grant access but must not inflate
 * the gross amount shown to operations.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class OrderStatsService {

    /** Discounted revenue is stored in yuan; the dashboard reports fen. */
    private static final int FEN_PER_YUAN = 2;

    private final TradeOrderMapper tradeOrderMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param tradeOrderMapper order statements
     * @author Caspian
     * @since 0.1.0
     */
    public OrderStatsService(TradeOrderMapper tradeOrderMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
    }

    /**
     * Returns order counts and paid revenue.
     *
     * @return order statistics
     * @author Caspian
     * @since 0.1.0
     */
    public OrderStatsView stats() {
        BigDecimal paidAmount = tradeOrderMapper.sumPaidAmount();
        long paidAmountFen =
                paidAmount == null ? 0L : paidAmount.movePointRight(FEN_PER_YUAN).longValueExact();
        return new OrderStatsView(
                tradeOrderMapper.countAll(), tradeOrderMapper.countPaid(), paidAmountFen);
    }
}
