package com.lp.common.identity;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.List;

/**
 * Request scoped user identity resolved from trusted headers.
 *
 * <p>The instance is bound to a {@link ThreadLocal} by {@link TrustedIdentityFilter} and cleared in
 * a {@code finally} block once the request completes. Business code may read it; only filters may
 * write it.
 *
 * @author Caspian
 * @since 0.1.0
 */
public final class UserContext {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private final long userId;

    private final List<String> roles;

    private final List<String> permissions;

    private final String source;

    private final String tenantId;

    /**
     * Creates an immutable request identity.
     *
     * @param userId authentic user id
     * @param roles role codes, {@code null} treated as empty
     * @param permissions permission codes, {@code null} treated as empty
     * @param source request source platform, may be {@code null}
     * @param tenantId tenant id, may be {@code null} in the single tenant release
     * @author Caspian
     * @since 0.1.0
     */
    public UserContext(
            long userId,
            List<String> roles,
            List<String> permissions,
            String source,
            String tenantId) {
        this.userId = userId;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        this.source = source;
        this.tenantId = tenantId;
    }

    /**
     * Creates an identity without permission codes.
     *
     * @param userId authentic user id
     * @param roles role codes, {@code null} treated as empty
     * @param source request source platform, may be {@code null}
     * @param tenantId tenant id, may be {@code null} in the single tenant release
     * @author Caspian
     * @since 0.1.0
     */
    public UserContext(long userId, List<String> roles, String source, String tenantId) {
        this(userId, roles, List.of(), source, tenantId);
    }

    /**
     * Binds the identity to the current thread.
     *
     * @param context identity to bind, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    /**
     * Returns the identity bound to the current thread.
     *
     * @return identity or {@code null} on whitelisted paths
     * @author Caspian
     * @since 0.1.0
     */
    public static UserContext get() {
        return HOLDER.get();
    }

    /**
     * Returns the identity bound to the current thread, failing when absent.
     *
     * @return non-null identity
     * @throws BizException with {@link ErrorCode#UNAUTHENTICATED} when no identity is bound
     * @author Caspian
     * @since 0.1.0
     */
    public static UserContext require() {
        UserContext context = HOLDER.get();
        if (context == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return context;
    }

    /**
     * Removes the identity from the current thread; always called by the filter.
     *
     * @author Caspian
     * @since 0.1.0
     */
    public static void clear() {
        HOLDER.remove();
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
     * Returns the immutable role list.
     *
     * @return role codes, never {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * Returns the immutable permission list.
     *
     * @return permission codes, never {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public List<String> getPermissions() {
        return permissions;
    }

    /**
     * Returns the request source platform.
     *
     * @return web, miniapp, admin or {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the reserved tenant id.
     *
     * @return tenant id or {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getTenantId() {
        return tenantId;
    }
}
