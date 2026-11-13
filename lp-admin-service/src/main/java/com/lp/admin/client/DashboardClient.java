package com.lp.admin.client;

import com.lp.admin.dto.AdminDashboardMetricsView;
import com.lp.admin.dto.AdminDashboardView;
import com.lp.admin.dto.AdminMetricsOverviewView;

/**
 * Loads the figures shown on the operations dashboard.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface DashboardClient {

    /**
     * Aggregates course, order and account totals.
     *
     * @return dashboard figures
     * @author Caspian
     * @since 0.1.0
     */
    AdminDashboardView load();

    /**
     * Aggregates the overview metrics of the last seven days.
     *
     * @return revenue, conversion, signups, best sellers and course counters
     * @author Caspian
     * @since 0.2.0
     */
    AdminMetricsOverviewView overview();

    /**
     * Aggregates the graphical dashboard figures of the trailing calendar window.
     *
     * @param days window length in days
     * @return revenue, orders, users, courses, refunds, daily trend and course ranking
     * @author Caspian
     * @since 0.21.0
     */
    AdminDashboardMetricsView dashboard(int days);
}
