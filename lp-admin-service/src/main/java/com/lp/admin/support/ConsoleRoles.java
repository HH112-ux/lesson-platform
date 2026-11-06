package com.lp.admin.support;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.Arrays;

/**
 * Role checks shared by the operations console endpoints.
 *
 * @author Caspian
 * @since 0.2.0
 */
public final class ConsoleRoles {

    private ConsoleRoles() {}

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
}
