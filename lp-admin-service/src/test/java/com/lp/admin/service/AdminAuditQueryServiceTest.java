package com.lp.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.admin.client.AdminAuditClient;
import com.lp.admin.dto.AdminAuditLogPageView;
import com.lp.common.exception.BizException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of the console audit query parsing and paging.
 *
 * @author Caspian
 * @since 0.6.0
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditQueryServiceTest {

    @Mock private AdminAuditClient adminAuditClient;

    private AdminAuditQueryService service;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @BeforeEach
    void setUp() {
        service = new AdminAuditQueryService(adminAuditClient);
    }

    /**
     * An ISO date-time string is parsed and the page size is clamped.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void parsesTimesAndClampsPaging() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 9, 0, 0);
        when(adminAuditClient.page(null, null, 55L, from, to, 1, 100))
                .thenReturn(new AdminAuditLogPageView(List.of(), 0, 1, 100));

        AdminAuditLogPageView page =
                service.page(null, null, 55L, "2026-11-01T00:00:00", "2026-11-09T00:00:00", 0, 500);

        assertEquals(1, page.pageNum());
        assertEquals(100, page.pageSize());
    }

    /**
     * A malformed bound is refused before the upstream call.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void rejectsMalformedTime() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> service.page(null, null, 55L, "2026-11-01", null, 1, 20));

        assertEquals(40001, exception.getCode());
        verify(adminAuditClient, org.mockito.Mockito.never())
                .page(any(), any(), any(), any(), any(), eq(1), eq(20));
    }

    /**
     * Object mode forwards the pair and leaves the time bounds empty.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void forwardsObjectMode() {
        when(adminAuditClient.page(eq(1), eq(21L), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(new AdminAuditLogPageView(List.of(), 0, 1, 20));

        service.page(1, 21L, null, null, null, 1, 20);

        verify(adminAuditClient).page(1, 21L, null, null, null, 1, 20);
    }
}
