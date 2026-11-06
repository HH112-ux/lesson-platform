package com.lp.course.dto;

import java.util.List;

/**
 * One page of the public course catalog.
 *
 * <p>Field names follow the frontend contract: {@code page}/{@code size} in, the same names plus
 * {@code total} and {@code items} out.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CoursePageView {

    private final List<PublicCourseView> items;

    private final long total;

    private final int page;

    private final int size;

    /**
     * Creates a page.
     *
     * @param items course cards
     * @param total total matching courses
     * @param page one based page number
     * @param size requested page size
     * @author Caspian
     * @since 0.1.0
     */
    public CoursePageView(List<PublicCourseView> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * Returns the course cards.
     *
     * @return cards, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    public List<PublicCourseView> getItems() {
        return items;
    }

    /**
     * Returns the total number of matching courses.
     *
     * @return total count
     * @author Caspian
     * @since 0.1.0
     */
    public long getTotal() {
        return total;
    }

    /**
     * Returns the current page number.
     *
     * @return one based page number
     * @author Caspian
     * @since 0.1.0
     */
    public int getPage() {
        return page;
    }

    /**
     * Returns the page size.
     *
     * @return page size
     * @author Caspian
     * @since 0.1.0
     */
    public int getSize() {
        return size;
    }
}
