package com.lp.auth.service;

import com.lp.auth.dto.UserMetricsView;
import com.lp.auth.dto.UserStatsView;
import com.lp.auth.mapper.AuthUserMapper;
import java.time.LocalDate;
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

    /** Largest accepted new-account window. */
    private static final int MAX_METRIC_DAYS = 90;

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

    /**
     * Returns the learner counts of a caller chosen window.
     *
     * <p>Only accounts holding the {@code STUDENT} role are counted, so teacher and operator
     * accounts never inflate the learner figure. The window is the same set of trailing calendar
     * days used by the trade dashboard, so both cards describe the same period.
     *
     * @param days window length in days, clamped to {@code 1..90}
     * @return learner total and learner accounts registered inside the window
     * @author Caspian
     * @since 0.21.0
     */
    public UserMetricsView metrics(int days) {
        int safeDays = Math.min(Math.max(days, 1), MAX_METRIC_DAYS);
        LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        return new UserMetricsView(
                authUserMapper.countStudents(), authUserMapper.countStudentsCreatedSince(since));
    }
}
