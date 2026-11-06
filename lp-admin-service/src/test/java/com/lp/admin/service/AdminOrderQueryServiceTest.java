package com.lp.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.admin.client.AdminOrderClient;
import com.lp.admin.dto.AdminOrderPageView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the console order list use case.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderQueryServiceTest {

    @Mock private AdminOrderClient adminOrderClient;

    @InjectMocks private AdminOrderQueryService adminOrderQueryService;

    /**
     * Every filter is forwarded unchanged.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void forwardsFilters() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        AdminOrderPageView page = new AdminOrderPageView(List.of(), 0L, 1, 20);
        when(adminOrderClient.page(1, 2, "LP", 3L, from, to, 1, 20)).thenReturn(page);

        AdminOrderPageView result = adminOrderQueryService.page(1, 2, "LP", 3L, from, to, 1, 20);

        assertThat(result).isSameAs(page);
        verify(adminOrderClient).page(1, 2, "LP", 3L, from, to, 1, 20);
    }

    /**
     * Out of range paging is clamped before reaching trade-service.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void clampsPaging() {
        when(adminOrderClient.page(null, null, null, null, null, null, 1, 100))
                .thenReturn(new AdminOrderPageView(List.of(), 0L, 1, 100));

        adminOrderQueryService.page(null, null, null, null, null, null, -3, 1000);

        verify(adminOrderClient).page(null, null, null, null, null, null, 1, 100);
    }
}
