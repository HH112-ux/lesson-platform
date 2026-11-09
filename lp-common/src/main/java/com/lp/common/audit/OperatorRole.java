package com.lp.common.audit;

/**
 * Operator roles stored in {@code admin_audit_log.operator_role}.
 *
 * <p>Only the two console roles that can perform a review are represented. The value is resolved by
 * admin-service from the authenticated gateway header, never supplied by the browser.
 *
 * @author Caspian
 * @since 0.6.0
 */
public final class OperatorRole {

    /** Operations role ({@code OPS}): {@code 0}. */
    public static final int OPS = 0;

    /** Administrator role ({@code ADMIN}): {@code 1}. */
    public static final int ADMIN = 1;

    private OperatorRole() {}

    /**
     * Checks whether a code is one of the supported operator roles.
     *
     * @param role candidate code, may be {@code null}
     * @return {@code true} for {@link #OPS} or {@link #ADMIN}
     * @author Caspian
     * @since 0.6.0
     */
    public static boolean isValid(Integer role) {
        return role != null && (role == OPS || role == ADMIN);
    }
}
