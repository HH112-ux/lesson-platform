package com.lp.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard summary served to the operations console.
 *
 * <p>The first five fields match the merged console contract exactly; the trailing three are
 * additive counters the console may render later and that older clients simply ignore.
 *
 * @param gmv settled revenue in yuan over the last seven days
 * @param orderCount orders created over the last seven days
 * @param paidRate paid conversion of the same window
 * @param newUsers accounts registered over the last seven days
 * @param courseSalesTop5 best selling courses of the window
 * @param courseTotal live courses
 * @param coursePendingReview courses waiting for review
 * @param userTotal live accounts
 * @author Caspian
 * @since 0.2.0
 */
public record AdminMetricsOverviewView(
        BigDecimal gmv,
        long orderCount,
        BigDecimal paidRate,
        long newUsers,
        List<AdminCourseSaleView> courseSalesTop5,
        long courseTotal,
        long coursePendingReview,
        long userTotal) {}
