package com.lp.course.mapper;

import com.lp.course.entity.Course;
import com.lp.course.entity.CourseLesson;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reads course content for service-to-service callers.
 *
 * <p>Every query filters on {@code deleted = 0} and projects explicit column aliases, so the
 * mapping does not depend on global underscore settings. Offline courses are still returned on
 * purpose: the caller decides whether the status blocks a trade.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface CourseMapper {

    /**
     * Finds a live course by id.
     *
     * @param courseId course primary key
     * @return course row including its live lesson count, or {@code null} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT c.id, c.title, c.price, c.status, c.owner_teacher_id AS ownerTeacherId, "
                    + "(SELECT COUNT(*) FROM course_lesson l "
                    + "WHERE l.course_id = c.id AND l.deleted = 0) AS lessonCount "
                    + "FROM course c WHERE c.id = #{courseId} AND c.deleted = 0 LIMIT 1")
    Course selectById(@Param("courseId") long courseId);

    /**
     * Finds live courses by a batch of ids.
     *
     * @param ids course primary keys, must not be empty
     * @return matching course rows, possibly fewer than {@code ids} when some are absent
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT c.id, c.title, c.price, c.status, "
                    + "c.owner_teacher_id AS ownerTeacherId, "
                    + "(SELECT COUNT(*) FROM course_lesson l "
                    + "WHERE l.course_id = c.id AND l.deleted = 0) AS lessonCount "
                    + "FROM course c WHERE c.deleted = 0 AND c.id IN "
                    + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach></script>")
    List<Course> selectByIds(@Param("ids") List<Long> ids);

    /**
     * Lists visible lessons of a course in outline order.
     *
     * @param courseId course primary key
     * @return lessons ordered by chapter and sort, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, course_id AS courseId, chapter_id AS chapterId, title, "
                    + "is_free AS isFree, sort FROM course_lesson "
                    + "WHERE course_id = #{courseId} AND deleted = 0 AND status = 1 "
                    + "ORDER BY chapter_id, sort, id")
    List<CourseLesson> selectLessons(@Param("courseId") long courseId);
}
