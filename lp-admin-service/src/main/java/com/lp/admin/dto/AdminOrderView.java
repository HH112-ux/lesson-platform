package com.lp.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One order row shown on the operations console.
 *
 * @param orderNo business order number
 * @param userId buyer id
 * @param status order status
 * @param payAmount payable amount in yuan
 * @param createdAt order creation time
 * @author Caspian
 * @since 0.2.0
 */
public record AdminOrderView(
        String orderNo,
        Long userId,
        Integer status,
        BigDecimal payAmount,
        LocalDateTime createdAt) {}
