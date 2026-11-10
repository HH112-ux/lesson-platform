package com.lp.admin.support;

import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role and permission checks shared by the operations console endpoints.
 *
 * @author Caspian
 * @since 0.2.0
 */
public final class ConsoleRoles {

    /** Permission code guarding the dashboard and metrics pages. */
    public static final String DASHBOARD_VIEW = "admin:dashboard:view";

    /** Permission code guarding the course management page. */
    public static final String COURSE_LIST = "admin:course:list";

    /** Permission code guarding pending courses, review decisions and audit history. */
    public static final String COURSE_REVIEW = "admin:course:review";

    /** Permission code guarding the account list. */
    public static final String USER_LIST = "admin:user:list";

    /** Permission code guarding the order list. */
    public static final String ORDER_LIST = "admin:order:list";

    /** Permission code guarding the teacher onboarding queue and its decisions. */
    public static final String TEACHER_AUDIT = "admin:teacher:audit";

    private ConsoleRoles() {}

    /**
     * Resolves the operator role written to the unified audit log.
     *
     * <p>{@code ADMIN} wins when the gateway header carries both roles, because the stricter role
     * is the one that explains the broader permission. The browser never supplies this value.
     *
     * @param roles gateway injected roles, comma separated
     * @return {@link OperatorRole#ADMIN} when {@code ADMIN} is present, otherwise {@link
     *     OperatorRole#OPS}
     * @throws BizException with {@code 40301} when the caller is not a console user
     * @author Caspian
     * @since 0.6.0
     */
    public static int operatorRole(String roles) {
        requireConsole(roles);
        boolean admin = Arrays.stream(roles.split(",")).map(String::trim).anyMatch("ADMIN"::equals);
        return admin ? OperatorRole.ADMIN : OperatorRole.OPS;
    }

    /**
     * Rejects callers that do not belong to the operations console.
     *
     * @param roles gateway injected roles, comma separated
     * @throws BizException with {@code 40301} when the roles do not contain {@code ADMIN} or {@code
     *     OPS}
     * @author Caspian
     * @since 0.2.0
     */
    public static void requireConsole(String roles) {
        boolean allowed =
                roles != null
                        && Arrays.stream(roles.split(","))
                                .map(String::trim)
                                .anyMatch(role -> "ADMIN".equals(role) || "OPS".equals(role));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * Rejects callers that do not hold the permission required by the endpoint.
     *
     * <p>An absent or empty permission header is always rejected: the gateway resolves codes from
     * the JWT, so emptiness means the token carried none and the caller must not be treated as a
     * superuser.
     *
     * @param permissions gateway injected permission codes, comma separated
     * @param required permission code the endpoint declares
     * @throws BizException with {@code 40301} when the code is missing
     * @author Caspian
     * @since 0.2.0
     */
    public static void requirePermission(String permissions, String required) {
        if (!parse(permissions).contains(required)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * Rejects callers whose permission header is absent or blank.
     *
     * @param permissions gateway injected permission codes, comma separated
     * @throws BizException with {@code 40301} when no code is present
     * @author Caspian
     * @since 0.2.0
     */
    public static void requirePermissions(String permissions) {
        if (parse(permissions).isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * Splits the permission header into a set.
     *
     * @param permissions comma separated permission codes, may be {@code null}
     * @return permission set, possibly empty
     * @author Caspian
     * @since 0.2.0
     */
    public static Set<String> parse(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
    }
}
