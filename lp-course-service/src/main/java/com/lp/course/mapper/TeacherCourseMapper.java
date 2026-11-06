package com.lp.course.mapper;

import com.lp.course.entity.Course;
import com.lp.course.entity.CourseChapter;
import com.lp.course.entity.CourseLesson;
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

    /**
     * Lists every live chapter of a course, including hidden ones.
     *
     * @param courseId course primary key
     * @return chapters ordered by sort
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, course_id AS courseId, title, sort, status FROM course_chapter "
                    + "WHERE course_id = #{courseId} AND deleted = 0 ORDER BY sort, id")
    List<CourseChapter> selectChapters(@Param("courseId") long courseId);

    /**
     * Lists every live lesson of a course, including hidden ones.
     *
     * @param courseId course primary key
     * @return lessons ordered by chapter and sort
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, course_id AS courseId, chapter_id AS chapterId, title, "
                    + "video_url AS videoUrl, video_duration_sec AS videoDurationSec, "
                    + "is_free AS isFree, sort, status FROM course_lesson "
                    + "WHERE course_id = #{courseId} AND deleted = 0 "
                    + "ORDER BY chapter_id, sort, id")
    List<CourseLesson> selectLessons(@Param("courseId") long courseId);

    /**
     * Finds one chapter inside its owning course.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @return chapter row, or {@code null} when absent or belonging to another course
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, course_id AS courseId, title, sort, status FROM course_chapter "
                    + "WHERE id = #{chapterId} AND course_id = #{courseId} AND deleted = 0 LIMIT 1")
    CourseChapter selectChapter(
            @Param("courseId") long courseId, @Param("chapterId") long chapterId);

    /**
     * Inserts a chapter and fills the generated id back into the row.
     *
     * @param chapter chapter to insert
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO course_chapter (course_id, title, sort, status) "
                    + "VALUES (#{courseId}, #{title}, #{sort}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChapter(CourseChapter chapter);

    /**
     * Updates the editable columns of one chapter.
     *
     * @param chapter chapter values to write
     * @return affected rows, {@code 0} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course_chapter SET title = #{title}, sort = #{sort}, status = #{status}, "
                    + "version = version + 1 WHERE id = #{id} AND course_id = #{courseId} "
                    + "AND deleted = 0")
    int updateChapter(CourseChapter chapter);

    /**
     * Logically deletes one chapter.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @return affected rows, {@code 0} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course_chapter SET deleted = 1, status = 0, version = version + 1 "
                    + "WHERE id = #{chapterId} AND course_id = #{courseId} AND deleted = 0")
    int deleteChapter(@Param("courseId") long courseId, @Param("chapterId") long chapterId);

    /**
     * Logically deletes every lesson left in a deleted chapter.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course_lesson SET deleted = 1, status = 0, version = version + 1 "
                    + "WHERE chapter_id = #{chapterId} AND course_id = #{courseId} AND deleted = 0")
    int deleteLessonsByChapter(
            @Param("courseId") long courseId, @Param("chapterId") long chapterId);

    /**
     * Finds one lesson inside its owning course and chapter.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @return lesson row, or {@code null} when absent or outside the chapter
     * @author Caspian
     * @since 0.1.0
     */
    @Select(
            "SELECT id, course_id AS courseId, chapter_id AS chapterId, title, "
                    + "video_url AS videoUrl, video_duration_sec AS videoDurationSec, "
                    + "is_free AS isFree, sort, status FROM course_lesson "
                    + "WHERE id = #{lessonId} AND chapter_id = #{chapterId} "
                    + "AND course_id = #{courseId} AND deleted = 0 LIMIT 1")
    CourseLesson selectLesson(
            @Param("courseId") long courseId,
            @Param("chapterId") long chapterId,
            @Param("lessonId") long lessonId);

    /**
     * Inserts a lesson and fills the generated id back into the row.
     *
     * @param lesson lesson to insert
     * @return affected rows
     * @author Caspian
     * @since 0.1.0
     */
    @Insert(
            "INSERT INTO course_lesson (course_id, chapter_id, title, video_url, "
                    + "video_duration_sec, is_free, sort, status) VALUES (#{courseId}, "
                    + "#{chapterId}, #{title}, #{videoUrl}, #{videoDurationSec}, #{isFree}, "
                    + "#{sort}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertLesson(CourseLesson lesson);

    /**
     * Updates the editable columns of one lesson.
     *
     * @param lesson lesson values to write
     * @return affected rows, {@code 0} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course_lesson SET title = #{title}, video_url = #{videoUrl}, "
                    + "video_duration_sec = #{videoDurationSec}, is_free = #{isFree}, "
                    + "sort = #{sort}, status = #{status}, version = version + 1 "
                    + "WHERE id = #{id} AND chapter_id = #{chapterId} "
                    + "AND course_id = #{courseId} AND deleted = 0")
    int updateLesson(CourseLesson lesson);

    /**
     * Logically deletes one lesson.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @return affected rows, {@code 0} when absent
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course_lesson SET deleted = 1, status = 0, version = version + 1 "
                    + "WHERE id = #{lessonId} AND chapter_id = #{chapterId} "
                    + "AND course_id = #{courseId} AND deleted = 0")
    int deleteLesson(
            @Param("courseId") long courseId,
            @Param("chapterId") long chapterId,
            @Param("lessonId") long lessonId);

    /**
     * Sends an offline course back to draft after its content changed.
     *
     * @param courseId course primary key
     * @return affected rows, {@code 0} when the course is not offline
     * @author Caspian
     * @since 0.1.0
     */
    @Update(
            "UPDATE course SET status = 0, version = version + 1 "
                    + "WHERE id = #{courseId} AND status = 3 AND deleted = 0")
    int resetOfflineToDraft(@Param("courseId") long courseId);
}
