package com.lp.auth.dto;

/**
 * Account counts used by the operations dashboard.
 *
 * @param totalUsers live accounts
 * @param newUsers accounts registered within the last seven days
 * @author Caspian
 * @since 0.1.0
 */
public record UserStatsView(long totalUsers, long newUsers) {}
