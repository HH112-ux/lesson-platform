package com.lp.auth.dto;

import java.util.List;

/**
 * One page of accounts served to the operations console.
 *
 * @param records accounts on the current page
 * @param total total accounts matching the filter
 * @param pageNum one based page number actually applied
 * @param pageSize page size actually applied
 * @author Caspian
 * @since 0.2.0
 */
public record AdminUserPageView(
        List<AdminUserView> records, long total, int pageNum, int pageSize) {}
