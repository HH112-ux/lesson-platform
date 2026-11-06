package com.lp.admin.dto;

import java.util.List;

/**
 * One node of the operations console menu tree.
 *
 * @param key stable node key, unique per tree
 * @param name display name
 * @param path front-end route, empty for action buttons
 * @param permission required permission code, for example {@code admin:course:review}
 * @param type 1 menu page, 2 action button
 * @param children child nodes, empty for leaves
 * @author Caspian
 * @since 0.1.0
 */
public record AdminMenuView(
        String key,
        String name,
        String path,
        String permission,
        int type,
        List<AdminMenuView> children) {}
