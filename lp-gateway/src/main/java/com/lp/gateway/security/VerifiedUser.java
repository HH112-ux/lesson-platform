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

    private final long userId;

    private final List<String> roles;

    /**
     * Creates an immutable identity.
     *
     * @param userId authentic user id
     * @param roles  role codes, may be empty
     * @author Caspian
     * @since 0.1.0
     */
    public VerifiedUser(long userId, List<String> roles) {
        this.userId = userId;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
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
     * Normalizes a {@code roles} claim into a bounded string list.
     *
     * @param claim raw claim value, may be {@code null} or another collection type
     * @return immutable list limited to {@link #MAX_ROLES} entries
     * @author Caspian
     * @since 0.1.0
     */
    public static List<String> rolesOf(Object claim) {
        if (!(claim instanceof Iterable<?> values)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (Object value : values) {
            if (value != null && roles.size() < MAX_ROLES) {
                roles.add(value.toString());
            }
        }
        return List.copyOf(roles);
    }
}
