package com.lp.trade.dto;

import java.time.LocalDateTime;

/**
 * One order row served to the operations console.
 *
 * @param orderNo business order number
 * @param userId buyer id
 * @param status status defined by {@link com.lp.trade.domain.OrderStatus}
 * @param payAmountFen payable amount in fen
 * @param createdAt order creation time
 * @author Caspian
 * @since 0.2.0
 */
public record AdminOrderView(
        String orderNo, Long userId, Integer status, long payAmountFen, LocalDateTime createdAt) {}
