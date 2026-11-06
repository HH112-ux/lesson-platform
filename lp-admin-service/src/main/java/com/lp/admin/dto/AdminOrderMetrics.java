package com.lp.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order metrics returned by trade-service for the dashboard window.
 *
 * @param gmv settled revenue in yuan
 * @param orderCount orders created inside the window
 * @param paidOrderCount paid orders created inside the window
 * @param paidRate {@code paidOrderCount / orderCount}
 * @param courseSalesTop5 best selling courses, highest first
 * @author Caspian
 * @since 0.2.0
 */
public record AdminOrderMetrics(
        BigDecimal gmv,
        long orderCount,
        long paidOrderCount,
        BigDecimal paidRate,
        List<AdminCourseSaleView> courseSalesTop5) {}
