package com.lp.auth.service;

import com.lp.auth.dto.UserStatsView;
import com.lp.auth.mapper.AuthUserMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * Read-only account aggregates for the operations dashboard.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class UserStatsService {

    /** Length in days of the new-account window shown on the dashboard. */
    private static final int NEW_ACCOUNT_DAYS = 7;

    private final AuthUserMapper authUserMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param authUserMapper account statements
     * @author Caspian
     * @since 0.1.0
     */
    public UserStatsService(AuthUserMapper authUserMapper) {
        this.authUserMapper = authUserMapper;
    }

    /**
     * Returns the number of live accounts and the accounts registered in the last seven days.
     *
     * @return account statistics
     * @author Caspian
     * @since 0.1.0
     */
    public UserStatsView stats() {
        LocalDateTime since = LocalDateTime.now().minusDays(NEW_ACCOUNT_DAYS);
        return new UserStatsView(
                authUserMapper.countUsers(), authUserMapper.countCreatedSince(since));
    }
}
