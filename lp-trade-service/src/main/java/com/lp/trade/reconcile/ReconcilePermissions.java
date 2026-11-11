package com.lp.trade.reconcile;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role and permission checks for the reconciliation endpoints.
 *
 * <p>The gateway resolves roles and permission codes from the JWT and injects them as trusted
 * headers. Only {@code ADMIN} and {@code OPS} may call these endpoints, and each endpoint also
 * declares a permission code. An absent header is always rejected.
 *
 * @author Caspian
 * @since 0.6.1
 */
public final class ReconcilePermissions {

    /** Permission required to read the daily reconciliation list. */
    public static final String VIEW = "admin:reconcile:view";

    /** Permission required to re-run a reconciliation. */
    public static final String RECHECK = "admin:reconcile:recheck";

    /** Permission required to import a bill or mark differences handled. */
    public static final String HANDLE = "admin:reconcile:handle";

    /** Permission required to close or void a settle batch; granted to {@code ADMIN} only. */
    public static final String CLOSE = "admin:reconcile:close";

    private ReconcilePermissions() {}

    /**
     * Rejects callers that are not operations staff or that lack the required permission.
     *
     * @param roles comma separated roles injected by the gateway, may be {@code null}
     * @param permissions comma separated permission codes injected by the gateway, may be {@code
     *     null}
     * @param required permission code the endpoint declares
     * @throws BizException with {@code 40301} when the caller is not allowed
     * @author Caspian
     * @since 0.6.1
     */
    public static void require(String roles, String permissions, String required) {
        boolean console =
                roles != null
                        && Arrays.stream(roles.split(","))
                                .map(String::trim)
                                .anyMatch(role -> "ADMIN".equals(role) || "OPS".equals(role));
        if (!console || !parse(permissions).contains(required)) {
            throw new BizException(ErrorCode.FORBIDDEN, "operations permission required");
        }
    }

    /**
     * Rejects callers that are not administrators or that lack the required permission.
     *
     * <p>Voiding a settle batch unlocks a bill day, so it is restricted to {@code ADMIN} even when
     * an {@code OPS} account carries the same permission code from a misconfigured role.
     *
     * @param roles comma separated roles injected by the gateway, may be {@code null}
     * @param permissions comma separated permission codes injected by the gateway, may be {@code
     *     null}
     * @param required permission code the endpoint declares
     * @throws BizException with {@code 40301} when the caller is not an administrator or lacks the
     *     permission
     * @author Caspian
     * @since 0.6.2
     */
    public static void requireAdmin(String roles, String permissions, String required) {
        boolean admin =
                roles != null
                        && Arrays.stream(roles.split(","))
                                .map(String::trim)
                                .anyMatch("ADMIN"::equals);
        if (!admin || !parse(permissions).contains(required)) {
            throw new BizException(ErrorCode.FORBIDDEN, "administrator permission required");
        }
    }

    private static Set<String> parse(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }
}
