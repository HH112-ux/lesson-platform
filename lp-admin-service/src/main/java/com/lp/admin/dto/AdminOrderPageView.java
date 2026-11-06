package com.lp.admin.dto;

import java.util.List;

/**
 * One page of orders shown on the operations console.
 *
 * @param records orders of this page
 * @param total total orders matching the filters
 * @param pageNum one based page number actually applied
 * @param pageSize page size actually applied
 * @author Caspian
 * @since 0.2.0
 */
public record AdminOrderPageView(
        List<AdminOrderView> records, long total, int pageNum, int pageSize) {}
