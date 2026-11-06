package com.lp.course.mapper;

import com.lp.course.entity.Course;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Write and scoped read access for the teacher console.
 *
 * <p>Every statement is scoped by {@code owner_teacher_id} in SQL where a teacher is the caller, so
 * a service layer mistake can never return another teacher's course. Administrators pass no owner
 * filter and are authorized separately.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Mapper
public interface TeacherCourseMapper {

    /**
     * Resolves the teacher profile of a signed-in user.
     *
     * @param userId authenticated user id
     * @return {@code teacher_profile.id}, or {@code null} when the user has no active profile
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id FROM teacher_profile WHERE user_id = #{userId} AND status = 1 "
                    + "AND deleted = 0 ORDER BY id LIMIT 1")
    Long selectTeacherIdByUser(@Param("userId") long userId);

    /**
     * Reads one course including the columns the teacher console exposes.
     *
     * @param courseId course primary key
     * @return course row, or {@code null} when absent or logically deleted
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, category_id AS categoryId, owner_teacher_id AS ownerTeacherId, title, "
                    + "subtitle, cover_url AS coverUrl, description, price, "
                    + "original_price AS originalPrice, level, status, "
                    + "review_remark AS reviewRemark, submitted_at AS submittedAt, "
                    + "updated_at AS updatedAt, "
                    + "(SELECT COUNT(*) FROM course_lesson l "
                    + "WHERE l.course_id = course.id AND l.deleted = 0) AS lessonCount "
                    + "FROM course WHERE id = #{courseId} AND deleted = 0 LIMIT 1")
    Course selectById(@Param("courseId") long courseId);

    /**
     * Counts the courses owned by one teacher, optionally filtered by status.
     *
     * @param teacherId {@code teacher_profile.id}, {@code null} for every teacher
     * @param status optional course status
     * @return matching courses
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT COUNT(*) FROM course WHERE deleted = 0 "
                    + "<if test='teacherId != null'>AND owner_teacher_id = #{teacherId} </if>"
                    + "<if test='status != null'>AND status = #{status} </if></script>")
    long countByOwner(@Param("teacherId") Long teacherId, @Param("status") Integer status);

    /**
     * Lists one page of the teacher's courses, most recently updated first.
     *
     * @param teacherId {@code teacher_profile.id}, {@code null} for every teacher
     * @param status optional course status
     * @param offset zero based row offset
     * @param limit maximum rows
     * @return course rows with the live lesson count
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "<script>SELECT id, category_id AS categoryId, owner_teacher_id AS ownerTeacherId, "
                    + "title, subtitle, cover_url AS coverUrl, description, price, "
                    + "original_price AS originalPrice, level, status, "
                    + "review_remark AS reviewRemark, submitted_at AS submittedAt, "
                    + "updated_at AS updatedAt, "
                    + "(SELECT COUNT(*) FROM course_lesson l "
                    + "WHERE l.course_id = course.id AND l.deleted = 0) AS lessonCount "
                    + "FROM course WHERE deleted = 0 "
                    + "<if test='teacherId != null'>AND owner_teacher_id = #{teacherId} </if>"
                    + "<if test='status != null'>AND status = #{status} </if>"
                    + "ORDER BY updated_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Course> selectPageByOwner(
            @Param("teacherId") Long teacherId,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * Inserts a draft course and fills the generated id back into the row.
     *
     * @param course course to insert, status is forced to draft
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO course (category_id, owner_teacher_id, title, subtitle, cover_url, "
                    + "description, price, original_price, instructor_id, level, status) "
                    + "VALUES (#{categoryId}, #{ownerTeacherId}, #{title}, #{subtitle}, "
                    + "#{coverUrl}, #{description}, #{price}, #{originalPrice}, "
                    + "#{ownerTeacherId}, #{level}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Course course);

    /**
     * Updates the editable columns of a draft or offline course.
     *
     * <p>An offline course returns to draft in the same statement (status {@code 3 -> 0}), because
     * changed content must pass review again. Pending and published courses never match.
     *
     * @param course course values to write
     * @return affected rows, {@code 0} when the course is no longer editable
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course SET category_id = #{categoryId}, title = #{title}, "
                    + "subtitle = #{subtitle}, cover_url = #{coverUrl}, "
                    + "description = #{description}, price = #{price}, "
                    + "original_price = #{originalPrice}, level = #{level}, "
                    + "status = CASE WHEN status = 3 THEN 0 ELSE status END, "
                    + "version = version + 1 "
                    + "WHERE id = #{id} AND status IN (0, 3) AND deleted = 0")
    int updateEditable(Course course);

    /**
     * Moves a draft or offline course into the review queue.
     *
     * @param courseId course primary key
     * @param submittedAt submit time
     * @return affected rows, {@code 0} when the course is not in a submittable status
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course SET status = 1, submitted_at = #{submittedAt}, "
                    + "review_remark = NULL, version = version + 1 "
                    + "WHERE id = #{courseId} AND status IN (0, 3) AND deleted = 0")
    int submit(@Param("courseId") long courseId, @Param("submittedAt") LocalDateTime submittedAt);

    /**
     * Counts the lessons that make a course submittable.
     *
     * <p>A lesson needs a playable object key and a positive duration; rows failing either test
     * cannot be reviewed and therefore do not count.
     *
     * @param courseId course primary key
     * @return number of submittable lessons
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT COUNT(*) FROM course_lesson WHERE course_id = #{courseId} "
                    + "AND deleted = 0 AND status = 1 AND video_url <> '' "
                    + "AND video_duration_sec > 0")
    long countSubmittableLessons(@Param("courseId") long courseId);
}
