package com.lp.gateway.security;

import java.util.ArrayList;
import java.util.List;

/**
 * Trusted identity extracted from a verified access token.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class VerifiedUser {

    /** Maximum number of role codes forwarded downstream. */
    public static final int MAX_ROLES = 20;

    /** Maximum number of permission codes forwarded downstream. */
    public static final int MAX_PERMISSIONS = 50;

    private final long userId;

    private final List<String> roles;

    private final List<String> permissions;

    /**
     * Creates an immutable identity.
     *
     * @param userId authentic user id
     * @param roles role codes, may be empty
     * @param permissions permission codes, may be empty
     * @author Caspian
     * @since 0.1.0
     */
    public VerifiedUser(long userId, List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /**
     * Returns the authenticated user id.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public long getUserId() {
        return userId;
    }

    /**
     * Returns the role codes.
     *
     * @return immutable role list
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Returns the permission codes.
     *
     * @return immutable permission list
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getPermissions() {
        return permissions;
    }

    /**
     * Normalizes a {@code roles} claim into a bounded string list.
     *
     * @param claim raw claim value, may be {@code null} or another collection type
     * @return immutable list limited to {@link #MAX_ROLES} entries
     * @author Caspian
     * @since 0.1.0
     */
    public static List<String> rolesOf(Object claim) {
        return codesOf(claim, MAX_ROLES);
    }

    /**
     * Normalizes a {@code permissions} claim into a bounded string list.
     *
     * @param claim raw claim value, may be {@code null} or another collection type
     * @return immutable list limited to {@link #MAX_PERMISSIONS} entries
     * @author Caspian
     * @since 0.1.0
     */
    public static List<String> permissionsOf(Object claim) {
        return codesOf(claim, MAX_PERMISSIONS);
    }

    /**
     * Converts a claim collection into a deduplicated, bounded code list.
     *
     * @param claim raw claim value
     * @param limit maximum number of entries
     * @return immutable code list
     * @author Caspian
     * @since 0.1.0
     */
    private static List<String> codesOf(Object claim, int limit) {
        if (!(claim instanceof Iterable<?> values)) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (Object value : values) {
            if (value != null && codes.size() < limit) {
                codes.add(value.toString());
            }
        }
        return List.copyOf(codes);
    }
}
