package com.lp.auth.dto;

/**
 * Cooling-off state of one account, reported to other services through the internal API.
 *
 * <p>The boolean is the decision; {@code effectiveAt} is diagnostic so the caller can log when the
 * restriction ends. A user who never applied is reported with {@code coolingOff=false}, which lets
 * the caller treat every ordinary buyer the same.
 *
 * @param userId account owner
 * @param coolingOff {@code true} while a cancellation request is in the cooling-off period
 * @param effectiveAt deadline in ISO local date time, {@code null} unless cooling off
 * @author Caspian
 * @since 0.3.0
 */
public record AccountDeletionStateView(long userId, boolean coolingOff, String effectiveAt) {}
