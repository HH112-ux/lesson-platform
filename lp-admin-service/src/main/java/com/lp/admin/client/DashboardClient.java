package com.lp.admin.client;

import com.lp.admin.dto.AdminDashboardView;

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
}
