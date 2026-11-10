package com.lp.trade.reconcile;

import java.util.List;

/**
 * One page of daily reconciliation aggregates.
 *
 * @param records aggregates of this page
 * @param total aggregates matching the filter
 * @param pageNum one based page number actually applied
 * @param pageSize page size actually applied
 * @author Caspian
 * @since 0.6.1
 */
public record ReconcilePageView(
        List<ReconcileView> records, long total, int pageNum, int pageSize) {}
