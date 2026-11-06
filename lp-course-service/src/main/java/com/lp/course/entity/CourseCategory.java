package com.lp.course.entity;

/**
 * Row read from {@code course_category}.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseCategory {

    private Long id;

    private String name;

    private String slug;

    private Integer sort;

    /**
     * Returns the category id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the category id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the display name.
     *
     * @return category name
     * @author Caspian
     * @since 0.1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name.
     *
     * @param name category name
     * @author Caspian
     * @since 0.1.0
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the stable English identifier.
     *
     * @return slug used by the catalog filter
     * @author Caspian
     * @since 0.1.0
     */
    public String getSlug() {
        return slug;
    }

    /**
     * Sets the stable English identifier.
     *
     * @param slug slug used by the catalog filter
     * @author Caspian
     * @since 0.1.0
     */
    public void setSlug(String slug) {
        this.slug = slug;
    }

    /**
     * Returns the sort weight.
     *
     * @return lower value sorts first
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * Sets the sort weight.
     *
     * @param sort lower value sorts first
     * @author Caspian
     * @since 0.1.0
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
