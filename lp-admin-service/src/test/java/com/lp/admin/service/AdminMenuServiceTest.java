package com.lp.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lp.admin.dto.AdminMenuView;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for menu tree filtering.
 *
 * @author Caspian
 * @since 0.1.0
 */
class AdminMenuServiceTest {

    private AdminMenuService service;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        service = new AdminMenuService();
    }

    /**
     * An empty permission set returns the whole tree for an authorised console caller.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void emptyPermissionsReturnFullTree() {
        List<AdminMenuView> menus = service.menusFor(Set.of());

        assertEquals(5, menus.size());
        assertEquals("dashboard", menus.get(0).key());
    }

    /**
     * Only nodes backed by a held permission survive.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void filtersNodesWithoutPermission() {
        List<AdminMenuView> menus = service.menusFor(Set.of("admin:order:list"));

        assertEquals(1, menus.size());
        assertEquals("orders", menus.get(0).key());
    }

    /**
     * A parent survives through a permitted child even when the parent code is absent.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void keepsParentWhenChildIsPermitted() {
        List<AdminMenuView> menus = service.menusFor(Set.of("admin:course:review"));

        assertEquals(1, menus.size());
        assertEquals("courses", menus.get(0).key());
        assertEquals(1, menus.get(0).children().size());
    }

    /**
     * A parent with an unheld child code drops the child.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void dropsChildWithoutPermission() {
        List<AdminMenuView> menus = service.menusFor(Set.of("admin:course:list"));

        assertEquals(1, menus.size());
        assertEquals("courses", menus.get(0).key());
        assertTrue(menus.get(0).children().isEmpty());
    }
}
