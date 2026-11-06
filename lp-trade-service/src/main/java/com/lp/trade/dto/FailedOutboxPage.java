package com.lp.trade.dto;

import java.util.List;

/**
 * One page of given up entitlement intents.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class FailedOutboxPage {

    private final int pageNum;

    private final int pageSize;

    private final long total;

    private final List<FailedOutboxView> records;

    /**
     * Creates one page.
     *
     * @param pageNum one based page number
     * @param pageSize page size after normalization
     * @param total total given up rows
     * @param records rows of this page
     * @author Caspian
     * @since 0.1.0
     */
    public FailedOutboxPage(int pageNum, int pageSize, long total, List<FailedOutboxView> records) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.records = records;
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
     * @return normalized page size
     * @author Caspian
     * @since 0.1.0
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Returns the total given up rows.
     *
     * @return total count
     * @author Caspian
     * @since 0.1.0
     */
    public long getTotal() {
        return total;
    }

    /**
     * Returns the rows of this page.
     *
     * @return rows, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    public List<FailedOutboxView> getRecords() {
        return records;
    }
}
