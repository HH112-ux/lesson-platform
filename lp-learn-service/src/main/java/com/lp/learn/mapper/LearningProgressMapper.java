package com.lp.learn.mapper;

import com.lp.learn.entity.LearningProgress;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reads and writes {@code learning_progress}.
 *
 * <p>The upsert relies on {@code uk_user_lesson}; {@code GREATEST} keeps the maximum monotonic even
 * when two reports race, and {@code completed} only ever moves from 0 to 1.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface LearningProgressMapper {

    /**
     * Loads the progress row of one student and one lesson.
     *
     * @param userId student user id
     * @param lessonId lesson id
     * @return row or {@code null} when the lesson was never reported
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, user_id AS userId, course_id AS courseId, lesson_id AS lessonId, "
                    + "position_seconds AS positionSeconds, "
                    + "max_position_seconds AS maxPositionSeconds, completed, "
                    + "updated_at AS updatedAt FROM learning_progress "
                    + "WHERE user_id = #{userId} AND lesson_id = #{lessonId} AND deleted = 0")
    LearningProgress selectOne(@Param("userId") long userId, @Param("lessonId") long lessonId);

    /**
     * Inserts or merges one progress row.
     *
     * @param progress computed row; its max position is already monotonic
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO learning_progress (user_id, course_id, lesson_id, position_seconds, "
                    + "max_position_seconds, completed) VALUES (#{userId}, #{courseId}, "
                    + "#{lessonId}, #{positionSeconds}, #{maxPositionSeconds}, #{completed}) "
                    + "ON DUPLICATE KEY UPDATE position_seconds = VALUES(position_seconds), "
                    + "max_position_seconds = GREATEST(max_position_seconds, "
                    + "VALUES(max_position_seconds)), "
                    + "completed = GREATEST(completed, VALUES(completed)), "
                    + "updated_at = CURRENT_TIMESTAMP(3)")
    int merge(LearningProgress progress);

    /**
     * Loads all lesson progress of one student in one course.
     *
     * @param userId student user id
     * @param courseId course id
     * @return rows ordered by lesson id, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, user_id AS userId, course_id AS courseId, lesson_id AS lessonId, "
                    + "position_seconds AS positionSeconds, "
                    + "max_position_seconds AS maxPositionSeconds, completed, "
                    + "updated_at AS updatedAt FROM learning_progress "
                    + "WHERE user_id = #{userId} AND course_id = #{courseId} AND deleted = 0 "
                    + "ORDER BY lesson_id")
    List<LearningProgress> selectByUserCourse(
            @Param("userId") long userId, @Param("courseId") long courseId);

    /**
     * Loads every reported lesson of one student across a page of courses.
     *
     * <p>Rows are ordered by course and by latest report first, so the caller can take the first
     * row of each course as the resume point without a second query.
     *
     * @param userId student user id
     * @param courseIds course ids of the current page, must not be empty
     * @return rows ordered by {@code course_id, updated_at DESC, lesson_id DESC}, possibly empty
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT id, user_id AS userId, course_id AS courseId, lesson_id AS lessonId, "
                    + "position_seconds AS positionSeconds, "
                    + "max_position_seconds AS maxPositionSeconds, completed, "
                    + "updated_at AS updatedAt FROM learning_progress "
                    + "WHERE user_id = #{userId} AND deleted = 0 AND course_id IN "
                    + "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>"
                    + "#{id}</foreach> "
                    + "ORDER BY course_id, updated_at DESC, lesson_id DESC</script>")
    List<LearningProgress> selectByUserCourses(
            @Param("userId") long userId, @Param("courseIds") List<Long> courseIds);
}
