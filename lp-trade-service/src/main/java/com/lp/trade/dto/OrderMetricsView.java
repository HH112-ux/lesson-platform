package com.lp.trade.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order metrics of the dashboard window.
 *
 * @param gmv settled revenue in yuan created inside the window
 * @param orderCount orders created inside the window
 * @param paidOrderCount paid orders created inside the window
 * @param paidRate {@code paidOrderCount / orderCount}, {@code 0} when nothing was created
 * @param courseSalesTop5 best selling courses inside the window, highest first
 * @author Caspian
 * @since 0.2.0
 */
public record OrderMetricsView(
        BigDecimal gmv,
        long orderCount,
        long paidOrderCount,
        BigDecimal paidRate,
        List<CourseSaleView> courseSalesTop5) {}
