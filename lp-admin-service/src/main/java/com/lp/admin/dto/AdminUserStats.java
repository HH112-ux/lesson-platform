package com.lp.admin.dto;

/**
 * Account counts returned by auth-service.
 *
 * @param totalUsers live accounts
 * @param newUsers accounts registered within the last seven days
 * @author Caspian
 * @since 0.1.0
 */
public record AdminUserStats(long totalUsers, long newUsers) {}
