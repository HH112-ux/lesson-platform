package com.lp.admin.service;

import com.lp.admin.client.DashboardClient;
import com.lp.admin.dto.AdminDashboardView;
import org.springframework.stereotype.Service;

/**
 * Dashboard use case; the figures are owned by the domain services, not by admin-service.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class AdminDashboardService {

    private final DashboardClient dashboardClient;

    /**
     * Creates the service with its aggregation port.
     *
     * @param dashboardClient dashboard figures
     * @author Caspian
     * @since 0.1.0
     */
    public AdminDashboardService(DashboardClient dashboardClient) {
        this.dashboardClient = dashboardClient;
    }

    /**
     * Loads the dashboard figures.
     *
     * @return aggregated figures
     * @author Caspian
     * @since 0.1.0
     */
    public AdminDashboardView load() {
        return dashboardClient.load();
    }
}
