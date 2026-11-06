package com.lp.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.admin.client.AdminUserClient;
import com.lp.admin.dto.AdminUserPageView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the console account list use case.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
class AdminUserQueryServiceTest {

    @Mock private AdminUserClient adminUserClient;

    @InjectMocks private AdminUserQueryService adminUserQueryService;

    /**
     * Filters and paging are forwarded unchanged.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void forwardsFilters() {
        AdminUserPageView page = new AdminUserPageView(List.of(), 0L, 2, 50);
        when(adminUserClient.page("tea", 1, 2, 50)).thenReturn(page);

        AdminUserPageView result = adminUserQueryService.page("tea", 1, 2, 50);

        assertThat(result).isSameAs(page);
        verify(adminUserClient).page("tea", 1, 2, 50);
    }

    /**
     * Out of range paging is clamped before reaching auth-service.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void clampsPaging() {
        when(adminUserClient.page(null, null, 1, 100))
                .thenReturn(new AdminUserPageView(List.of(), 0L, 1, 100));

        adminUserQueryService.page(null, null, 0, 500);

        verify(adminUserClient).page(null, null, 1, 100);
    }
}
