package com.lp.course.dto;

import java.util.List;

/**
 * One page of the operations course review queue.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PendingCoursePageView {

    private final List<PendingCourseView> records;

    private final long total;

    private final int pageNum;

    private final int pageSize;

    /**
     * Creates the page.
     *
     * @param records pending courses of this page
     * @param total total courses waiting for review
     * @param pageNum one based page number
     * @param pageSize page size
     * @author Caspian
     * @since 0.1.0
     */
    public PendingCoursePageView(
            List<PendingCourseView> records, long total, int pageNum, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /**
     * Returns the pending courses of this page.
     *
     * @return pending courses
     * @author Caspian
     * @since 0.1.0
     */
    public List<PendingCourseView> getRecords() {
        return records;
    }

    /**
     * Returns the total courses waiting for review.
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
