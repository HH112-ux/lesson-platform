package com.lp.course.mapper;

import com.lp.course.entity.CourseCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reads the course taxonomy from {@code course_category}.
 *
 * <p>Only enabled, non-deleted categories are exposed to the public catalog.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface CourseCategoryMapper {

    /**
     * Lists visible categories in display order.
     *
     * @return categories ordered by sort and id, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, name, slug, sort FROM course_category "
                    + "WHERE deleted = 0 AND status = 1 ORDER BY sort, id")
    List<CourseCategory> selectVisible();

    /**
     * Finds one visible category by slug.
     *
     * @param slug stable identifier
     * @return matching category or {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, name, slug, sort FROM course_category "
                    + "WHERE deleted = 0 AND status = 1 AND slug = #{slug} LIMIT 1")
    CourseCategory selectVisibleBySlug(@Param("slug") String slug);

    /**
     * Finds one visible category by id.
     *
     * @param id category primary key
     * @return matching category or {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, name, slug, sort FROM course_category "
                    + "WHERE deleted = 0 AND status = 1 AND id = #{id} LIMIT 1")
    CourseCategory selectVisibleById(@Param("id") long id);
}
