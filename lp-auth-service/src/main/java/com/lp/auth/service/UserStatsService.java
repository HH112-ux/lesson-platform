package com.lp.auth.service;

import com.lp.auth.dto.UserStatsView;
import com.lp.auth.mapper.AuthUserMapper;
import org.springframework.stereotype.Service;

/**
 * Read-only account aggregates for the operations dashboard.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class UserStatsService {

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
     * Returns the number of live accounts.
     *
     * @return account statistics
     * @author Caspian
     * @since 0.1.0
     */
    public UserStatsView stats() {
        return new UserStatsView(authUserMapper.countUsers());
    }
}
