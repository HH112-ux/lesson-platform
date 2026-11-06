package com.lp.course.dto;

/**
 * One category entry rendered by the web filter bar.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class CourseCategoryView {

    private final long categoryId;

    private final String name;

    private final String slug;

    /**
     * Creates a category view.
     *
     * @param categoryId category primary key
     * @param name display name
     * @param slug stable identifier accepted by {@code GET /api/v1/courses}
     * @author Caspian
     * @since 0.1.0
     */
    public CourseCategoryView(long categoryId, String name, String slug) {
        this.categoryId = categoryId;
        this.name = name;
        this.slug = slug;
    }

    /**
     * Returns the category id.
     *
     * @return category primary key
     * @author Caspian
     * @since 0.1.0
     */
    public long getCategoryId() {
        return categoryId;
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
     * Returns the stable identifier.
     *
     * @return slug
     * @author Caspian
     * @since 0.1.0
     */
    public String getSlug() {
        return slug;
    }
}
