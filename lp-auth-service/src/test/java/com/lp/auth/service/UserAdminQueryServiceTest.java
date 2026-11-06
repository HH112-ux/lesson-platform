package com.lp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.auth.dto.AdminUserPageView;
import com.lp.auth.entity.AdminUserRow;
import com.lp.auth.mapper.AuthUserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the console account list.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
class UserAdminQueryServiceTest {

    @Mock private AuthUserMapper authUserMapper;

    @InjectMocks private UserAdminQueryService userAdminQueryService;

    /**
     * Rows are mapped field by field and roles come from the role query.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void mapsRowsAndRoles() {
        AdminUserRow row = new AdminUserRow();
        row.setUserId(7L);
        row.setUsername("teacher01");
        row.setNickname("Teacher One");
        row.setPhone("[PHONE_1]");
        row.setStatus(1);
        row.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        when(authUserMapper.countForAdmin("tea", 1)).thenReturn(1L);
        when(authUserMapper.selectPageForAdmin("tea", 1, 0L, 20L)).thenReturn(List.of(row));
        when(authUserMapper.selectRoleCodes(7L)).thenReturn(List.of("TEACHER"));

        AdminUserPageView page = userAdminQueryService.page(" tea ", 1, 1, 20);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).username()).isEqualTo("teacher01");
        assertThat(page.records().get(0).roles()).containsExactly("TEACHER");
    }

    /**
     * An empty result skips the page query entirely.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void skipsQueryWhenNothingMatches() {
        when(authUserMapper.countForAdmin("nobody", null)).thenReturn(0L);

        AdminUserPageView page = userAdminQueryService.page("nobody", null, 1, 20);

        assertThat(page.records()).isEmpty();
        verify(authUserMapper, never()).selectPageForAdmin(any(), any(), anyLong(), anyLong());
    }

    /**
     * Page number and size are clamped to the accepted range.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void clampsPaging() {
        when(authUserMapper.countForAdmin(null, null)).thenReturn(1L);
        when(authUserMapper.selectPageForAdmin(null, null, 0L, 100L)).thenReturn(List.of());

        AdminUserPageView page = userAdminQueryService.page(null, null, 0, 500);

        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(100);
        assertThat(page.records()).isEmpty();
    }
}
