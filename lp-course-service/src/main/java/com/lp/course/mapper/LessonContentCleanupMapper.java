package com.lp.course.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Registers the storage objects left behind by deleted lessons.
 *
 * <p>Registration runs in the same transaction as the logical deletion, so a committed deletion
 * never loses its object key. Only the source object held in {@code course_lesson.video_url} is
 * registered: transcoded products are not derived here. A blank key means the lesson never had a
 * confirmed upload, and it is never registered, because the unique key on {@code object_key} would
 * reject the second blank row.
 *
 * @author Caspian
 * @since 0.16.0
 */
@Mapper
public interface LessonContentCleanupMapper {

    /**
     * Registers the object key of one lesson inside its chapter.
     *
     * <p>Runs before the lesson is logically deleted, so the statement still sees the live row. A
     * blank key inserts no row, and the unique key on {@code object_key} absorbs a repeated or
     * concurrent deletion.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @return affected rows, {@code 0} when the lesson has no object or is already gone
     * @author Caspian
     * @since 0.16.0
     */
    @Insert(
            "INSERT INTO lesson_content_cleanup (course_id, object_key) "
                    + "SELECT l.course_id, l.video_url FROM course_lesson l "
                    + "WHERE l.id = #{lessonId} AND l.chapter_id = #{chapterId} "
                    + "AND l.course_id = #{courseId} AND l.deleted = 0 "
                    + "AND TRIM(l.video_url) <> '' "
                    + "ON DUPLICATE KEY UPDATE object_key = object_key")
    int insertLessonCleanup(
            @Param("courseId") long courseId,
            @Param("chapterId") long chapterId,
            @Param("lessonId") long lessonId);

    /**
     * Registers every object key of a chapter in one statement.
     *
     * <p>Runs before the chapter and its lessons are logically deleted, so the batch still sees the
     * live rows. A single {@code INSERT ... SELECT} keeps the deletion transaction short instead of
     * one round trip per lesson, and blank keys are filtered here for the same reason as the single
     * lesson statement.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @return number of registered objects
     * @author Caspian
     * @since 0.16.0
     */
    @Insert(
            "INSERT INTO lesson_content_cleanup (course_id, object_key) "
                    + "SELECT l.course_id, l.video_url FROM course_lesson l "
                    + "WHERE l.chapter_id = #{chapterId} AND l.course_id = #{courseId} "
                    + "AND l.deleted = 0 AND TRIM(l.video_url) <> '' "
                    + "ON DUPLICATE KEY UPDATE object_key = object_key")
    int insertChapterLessonsCleanup(
            @Param("courseId") long courseId, @Param("chapterId") long chapterId);

    /**
     * Registers one known object key, used when a confirmation overwrites an older video.
     *
     * <p>Both values are bound here, so the caller filters blank keys; repeats keep one row.
     *
     * @param courseId course primary key
     * @param objectKey object key that lost its last reference
     * @return affected rows, {@code 1} when a row was inserted
     * @author Caspian
     * @since 0.16.0
     */
    @Insert(
            "INSERT INTO lesson_content_cleanup (course_id, object_key) "
                    + "VALUES (#{courseId}, #{objectKey}) "
                    + "ON DUPLICATE KEY UPDATE object_key = object_key")
    int insertObjectKeyCleanup(
            @Param("courseId") long courseId, @Param("objectKey") String objectKey);
}
