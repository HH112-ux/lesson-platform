package com.lp.auth.dto;

/**
 * Account counts used by the operations dashboard.
 *
 * @param totalUsers live accounts
 * @author Caspian
 * @since 0.1.0
 */
public record UserStatsView(long totalUsers) {}
