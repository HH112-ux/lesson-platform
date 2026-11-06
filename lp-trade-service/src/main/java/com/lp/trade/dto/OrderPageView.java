package com.lp.trade.dto;

import java.util.List;

/**
 * One page of buyer orders.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class OrderPageView {

    private final List<OrderSummaryView> records;

    private final long total;

    private final int pageNum;

    private final int pageSize;

    /**
     * Creates an order page.
     *
     * @param records orders of this page
     * @param total total matching orders
     * @param pageNum one based page number
     * @param pageSize page size
     * @author Caspian
     * @since 0.1.0
     */
    public OrderPageView(List<OrderSummaryView> records, long total, int pageNum, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /**
     * Returns the orders of this page.
     *
     * @return order rows
     * @author Caspian
     * @since 0.1.0
     */
    public List<OrderSummaryView> getRecords() {
        return records;
    }

    /**
     * Returns the total matching orders.
     *
     * @return total count
     * @author Caspian
     * @since 0.1.0
     */
    public long getTotal() {
        return total;
    }

    /**
     * Returns the page number.
     *
     * @return one based page number
     * @author Caspian
     * @since 0.1.0
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * Returns the page size.
     *
     * @return page size
     * @author Caspian
     * @since 0.1.0
     */
    public int getPageSize() {
        return pageSize;
    }
}
