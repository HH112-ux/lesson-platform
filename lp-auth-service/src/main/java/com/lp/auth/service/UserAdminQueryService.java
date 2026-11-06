package com.lp.auth.service;

import com.lp.auth.dto.AdminUserPageView;
import com.lp.auth.dto.AdminUserView;
import com.lp.auth.entity.AdminUserRow;
import com.lp.auth.mapper.AuthUserMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-only account list for the operations console.
 *
 * <p>Roles are loaded per row on purpose: a console page holds at most {@value #MAX_PAGE_SIZE}
 * accounts, and a batched role query would need a second projection just for one string list.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Service
public class UserAdminQueryService {

    /** Maximum page size accepted from the console. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AuthUserMapper authUserMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param authUserMapper account statements
     * @author Caspian
     * @since 0.2.0
     */
    public UserAdminQueryService(AuthUserMapper authUserMapper) {
        this.authUserMapper = authUserMapper;
    }

    /**
     * Lists one page of accounts, newest registration first.
     *
     * @param keyword optional match on username, nickname or phone; blank means no filter
     * @param status optional account status filter
     * @param pageNum one based page number
     * @param pageSize page size, clamped to {@code 1..100}
     * @return the page together with the total match count
     * @author Caspian
     * @since 0.2.0
     */
    public AdminUserPageView page(String keyword, Integer status, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        long total = authUserMapper.countForAdmin(normalizedKeyword, status);
        if (total == 0) {
            return new AdminUserPageView(List.of(), 0L, safePage, safeSize);
        }
        List<AdminUserRow> rows =
                authUserMapper.selectPageForAdmin(
                        normalizedKeyword, status, (long) (safePage - 1) * safeSize, safeSize);
        List<AdminUserView> records =
                rows.stream()
                        .map(
                                row ->
                                        new AdminUserView(
                                                row.getUserId(),
                                                row.getUsername(),
                                                row.getNickname(),
                                                row.getPhone(),
                                                row.getStatus(),
                                                authUserMapper.selectRoleCodes(row.getUserId()),
                                                row.getCreatedAt()))
                        .toList();
        return new AdminUserPageView(records, total, safePage, safeSize);
    }
}
