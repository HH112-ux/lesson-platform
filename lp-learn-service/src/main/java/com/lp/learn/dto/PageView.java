package com.lp.learn.dto;

import java.util.List;

/**
 * Paginated payload, matching the platform envelope {@code {pageNum,pageSize,total,records}}.
 *
 * @param <T> row type
 * @author Caspian
 * @since 0.1.0
 */
public class PageView<T> {

    private final int pageNum;

    private final int pageSize;

    private final long total;

    private final List<T> records;

    /**
     * Creates one page.
     *
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @param total total matching rows
     * @param records rows of this page
     * @author Caspian
     * @since 0.1.0
     */
    public PageView(int pageNum, int pageSize, long total, List<T> records) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.records = records;
    }

    /**
     * Returns the one based page number.
     *
     * @return page number
     * @author Caspian
     * @since 0.1.0
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * Returns the requested page size.
     *
     * @return page size
     * @author Caspian
     * @since 0.1.0
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Returns the total matching rows.
     *
     * @return total row count
     * @author Caspian
     * @since 0.1.0
     */
    public long getTotal() {
        return total;
    }

    /**
     * Returns the rows of this page.
     *
     * @return immutable row list
     * @author Caspian
     * @since 0.1.0
     */
    public List<T> getRecords() {
        return records;
    }
}
