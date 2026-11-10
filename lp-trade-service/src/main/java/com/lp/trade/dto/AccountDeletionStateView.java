package com.lp.trade.dto;

/**
 * Cooling-off state of a buyer as reported by the auth-service internal API.
 *
 * @param userId account owner
 * @param coolingOff {@code true} while a cancellation request is in the cooling-off period
 * @param effectiveAt deadline in ISO local date time, {@code null} unless cooling off
 * @author Caspian
 * @since 0.3.0
 */
public record AccountDeletionStateView(long userId, boolean coolingOff, String effectiveAt) {}
