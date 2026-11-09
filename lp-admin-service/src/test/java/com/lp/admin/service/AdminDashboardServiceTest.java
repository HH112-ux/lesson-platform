package com.lp.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.lp.admin.client.DashboardClient;
import com.lp.admin.dto.AdminDashboardView;
import com.lp.admin.dto.AdminMetricsOverviewView;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for dashboard delegation.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private DashboardClient dashboardClient;

    @InjectMocks private AdminDashboardService service;

    /**
     * The service returns the figures produced by the aggregation port unchanged.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void returnsAggregatedFigures() {
        AdminDashboardView expected = new AdminDashboardView(12, 3, 8, 40, 30, 299000, 77);
        when(dashboardClient.load()).thenReturn(expected);

        AdminDashboardView actual = service.load();

        assertEquals(expected, actual);
    }

    /**
     * The overview figures are returned unchanged.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void returnsOverviewFigures() {
        AdminMetricsOverviewView expected =
                new AdminMetricsOverviewView(
                        10000L, 4, new BigDecimal("0.2500"), 2, List.of(), 12, 3, 77);
        when(dashboardClient.overview()).thenReturn(expected);

        AdminMetricsOverviewView actual = service.overview();

        assertEquals(expected, actual);
    }
}
