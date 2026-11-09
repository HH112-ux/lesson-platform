package com.lp.admin.service;

import com.lp.admin.dto.AdminMenuView;
import com.lp.admin.support.ConsoleRoles;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Builds the operations console menu tree.
 *
 * <p>The tree mirrors the permission codes seeded in {@code sys_menu}; it is defined in code
 * because admin-service does not own a datasource. A node is kept only when the caller holds its
 * permission, and a parent survives when any child survives. An empty permission set yields an
 * empty tree: the gateway always resolves permissions from the JWT, so emptiness means no codes
 * were granted and must never be upgraded to the full tree.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class AdminMenuService {

    private static final String DASHBOARD = "admin:dashboard:view";
    private static final String COURSE_LIST = "admin:course:list";
    private static final String COURSE_REVIEW = "admin:course:review";
    private static final String USER_LIST = "admin:user:list";
    private static final String ORDER_LIST = "admin:order:list";

    /** Menu type of a routable page. */
    private static final int TYPE_MENU = 1;

    /** Menu type of an action button. */
    private static final int TYPE_ACTION = 2;

    private static final List<AdminMenuView> MENUS =
            List.of(
                    new AdminMenuView(
                            "dashboard",
                            "工作台",
                            "/admin/dashboard",
                            DASHBOARD,
                            TYPE_MENU,
                            List.of()),
                    new AdminMenuView(
                            "courses",
                            "课程管理",
                            "/admin/courses",
                            COURSE_LIST,
                            TYPE_MENU,
                            List.of(
                                    new AdminMenuView(
                                            "course-review",
                                            "审核课程",
                                            "",
                                            COURSE_REVIEW,
                                            TYPE_ACTION,
                                            List.of()))),
                    new AdminMenuView(
                            "users", "用户管理", "/admin/users", USER_LIST, TYPE_MENU, List.of()),
                    new AdminMenuView(
                            "orders", "订单管理", "/admin/orders", ORDER_LIST, TYPE_MENU, List.of()),
                    new AdminMenuView(
                            "teachers",
                            "教师管理",
                            "/admin/teachers",
                            ConsoleRoles.TEACHER_AUDIT,
                            TYPE_MENU,
                            List.of()),
                    new AdminMenuView(
                            "audit-logs",
                            "审计日志",
                            "/admin/audit-logs",
                            ConsoleRoles.AUDIT_LIST,
                            TYPE_MENU,
                            List.of()));

    /**
     * Returns the menu tree visible to a permission set.
     *
     * @param permissions permission codes of the caller, may be empty
     * @return filtered menu tree; empty when no permission is held
     * @author Caspian
     * @since 0.1.0
     */
    public List<AdminMenuView> menusFor(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return filter(MENUS, permissions);
    }

    private List<AdminMenuView> filter(List<AdminMenuView> nodes, Set<String> permissions) {
        return nodes.stream()
                .map(node -> filter(node, permissions))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private AdminMenuView filter(AdminMenuView node, Set<String> permissions) {
        List<AdminMenuView> children = filter(node.children(), permissions);
        if (permissions.contains(node.permission())) {
            return new AdminMenuView(
                    node.key(), node.name(), node.path(), node.permission(), node.type(), children);
        }
        if (!children.isEmpty()) {
            return new AdminMenuView(
                    node.key(), node.name(), node.path(), node.permission(), node.type(), children);
        }
        return null;
    }
}
