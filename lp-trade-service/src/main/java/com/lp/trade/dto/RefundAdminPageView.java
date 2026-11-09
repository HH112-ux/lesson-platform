package com.lp.trade.dto;

import java.util.List;

/**
 * One page of refunds for the operations review list.
 *
 * @param records refunds of this page
 * @param total total refunds matching the filter
 * @param pageNum one based page number actually applied
 * @param pageSize page size actually applied
 * @author Caspian
 * @since 0.1.0
 */
public record RefundAdminPageView(
        List<RefundAdminView> records, long total, int pageNum, int pageSize) {}
