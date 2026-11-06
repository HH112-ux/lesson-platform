package com.lp.admin.dto;

/**
 * Account counts returned by auth-service.
 *
 * @param totalUsers live accounts
 * @author Caspian
 * @since 0.1.0
 */
public record AdminUserStats(long totalUsers) {}
