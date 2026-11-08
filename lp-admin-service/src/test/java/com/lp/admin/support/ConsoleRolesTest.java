package com.lp.admin.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lp.common.exception.BizException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the console role and permission guards.
 *
 * @author Caspian
 * @since 0.2.0
 */
class ConsoleRolesTest {

    /**
     * A held code passes and a missing code is rejected with 403.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void requiresDeclaredPermission() {
        assertDoesNotThrow(
                () ->
                        ConsoleRoles.requirePermission(
                                "admin:course:list,admin:course:review",
                                ConsoleRoles.COURSE_REVIEW));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                ConsoleRoles.requirePermission(
                                        "admin:course:list", ConsoleRoles.COURSE_REVIEW));

        assertEquals(40301, exception.getCode());
    }

    /**
     * An absent or blank header is rejected instead of being treated as a superuser.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void rejectsEmptyPermissions() {
        assertEquals(
                40301,
                assertThrows(BizException.class, () -> ConsoleRoles.requirePermissions(null))
                        .getCode());
        assertEquals(
                40301,
                assertThrows(BizException.class, () -> ConsoleRoles.requirePermissions(" "))
                        .getCode());
        assertEquals(
                40301,
                assertThrows(
                                BizException.class,
                                () -> ConsoleRoles.requirePermission("", ConsoleRoles.ORDER_LIST))
                        .getCode());
    }

    /**
     * Parsing trims codes, drops blanks and de-duplicates.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void parsesAndNormalizesPermissionHeader() {
        Set<String> codes =
                ConsoleRoles.parse(" admin:order:list , ,admin:order:list,admin:user:list ");

        assertEquals(Set.of("admin:order:list", "admin:user:list"), codes);
        assertEquals(Set.of(), ConsoleRoles.parse(null));
    }
}
