package com.lp.trade.reconcile;

import java.util.List;

/**
 * One page of settlement batches.
 *
 * @param records batches of this page
 * @param total batches matching the filter
 * @param pageNum one based page number actually applied
 * @param pageSize page size actually applied
 * @author Caspian
 * @since 0.6.2
 */
public record SettlementPageView(
        List<SettlementView> records, long total, int pageNum, int pageSize) {}
