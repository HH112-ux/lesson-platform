package com.lp.course.mapper;

import com.lp.course.cleanup.LessonCleanupRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Registers the storage objects left behind by deleted lessons.
 *
 * <p>Registration runs in the same transaction as the logical deletion, so a committed deletion
 * never loses its object key. Only the source object held in {@code course_lesson.video_url} is
 * registered: transcoded products are not derived here. A blank key means the lesson never had a
 * confirmed upload, and it is never registered, because the unique key on {@code object_key} would
 * reject the second blank row.
 *
 * <p>The same mapper owns the queue statements of the cleanup worker: batch selection, claim,
 * defer, retry and terminal states, plus the stale reclaim and the three reference counts. Those
 * counts must run at cleanup time and never come from a registration snapshot, because a lesson or
 * a transcode may start pointing at the object again after the row was registered. Status values
 * are listed in {@code LessonCleanupStatus}.
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

    /**
     * Reads the next batch of rows that are due for cleanup.
     *
     * @param now rows scheduled for a later time are skipped
     * @param limit batch size
     * @return due rows ordered by primary key, which follows insertion order
     * @author Caspian
     * @since 0.17.0
     */
    @Select(
            "SELECT id, object_key AS objectKey, attempt_count AS attemptCount "
                    + "FROM lesson_content_cleanup "
                    + "WHERE status = 0 AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
                    + "ORDER BY id LIMIT #{limit}")
    List<LessonCleanupRow> selectPending(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * Claims one row so a second tick skips it.
     *
     * @param id row primary key
     * @return affected rows, {@code 0} when another tick won the race
     * @author Caspian
     * @since 0.17.0
     */
    @Update("UPDATE lesson_content_cleanup SET status = 1 WHERE id = #{id} AND status = 0")
    int claim(@Param("id") long id);

    /**
     * Pushes a still-referenced object to a later re-check without spending an attempt.
     *
     * <p>An object referenced by a live lesson is deferred forever by design; that is not a failure
     * and must not consume the retry budget.
     *
     * @param id row primary key
     * @param nextRetryAt next time the row becomes due
     * @return affected rows
     * @author Caspian
     * @since 0.17.0
     */
    @Update(
            "UPDATE lesson_content_cleanup SET status = 0, next_retry_at = #{nextRetryAt}, "
                    + "last_error = NULL WHERE id = #{id} AND status = 1")
    int defer(@Param("id") long id, @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /**
     * Schedules another attempt after a transient failure.
     *
     * @param id row primary key
     * @param attempts attempt count to store, already incremented by the caller
     * @param nextRetryAt next time the row becomes due
     * @param error short message for operators
     * @return affected rows
     * @author Caspian
     * @since 0.17.0
     */
    @Update(
            "UPDATE lesson_content_cleanup SET status = 0, attempt_count = #{attempts}, "
                    + "next_retry_at = #{nextRetryAt}, last_error = #{error} "
                    + "WHERE id = #{id} AND status = 1")
    int scheduleRetry(
            @Param("id") long id,
            @Param("attempts") int attempts,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("error") String error);

    /**
     * Marks one row as successfully cleaned.
     *
     * @param id row primary key
     * @return affected rows
     * @author Caspian
     * @since 0.17.0
     */
    @Update(
            "UPDATE lesson_content_cleanup SET status = 2, last_error = NULL "
                    + "WHERE id = #{id} AND status = 1")
    int markDone(@Param("id") long id);

    /**
     * Marks one row as terminal and leaves it for a human.
     *
     * @param id row primary key
     * @param attempts attempt count to store, already incremented by the caller
     * @param error short message for operators
     * @return affected rows
     * @author Caspian
     * @since 0.17.0
     */
    @Update(
            "UPDATE lesson_content_cleanup SET status = 3, attempt_count = #{attempts}, "
                    + "last_error = #{error} WHERE id = #{id} AND status = 1")
    int markFailed(
            @Param("id") long id, @Param("attempts") int attempts, @Param("error") String error);

    /**
     * Returns rows left running by a crashed tick to the pending queue.
     *
     * @param threshold rows updated before this instant are considered abandoned
     * @return number of reclaimed rows
     * @author Caspian
     * @since 0.17.0
     */
    @Update(
            "UPDATE lesson_content_cleanup SET status = 0, attempt_count = attempt_count + 1 "
                    + "WHERE status = 1 AND updated_at < #{threshold}")
    int reclaimStale(@Param("threshold") LocalDateTime threshold);

    /**
     * Counts live lessons that still point at the object.
     *
     * @param objectKey object key to look for
     * @return number of lessons referencing it
     * @author Caspian
     * @since 0.17.0
     */
    @Select("SELECT COUNT(*) FROM course_lesson WHERE video_url = #{objectKey} AND deleted = 0")
    int countLessonReferences(@Param("objectKey") String objectKey);

    /**
     * Counts upload sessions that may still confirm the object.
     *
     * @param objectKey object key to look for
     * @return number of pending upload sessions
     * @author Caspian
     * @since 0.17.0
     */
    @Select("SELECT COUNT(*) FROM lesson_upload WHERE object_key = #{objectKey} AND status = 0")
    int countPendingUploads(@Param("objectKey") String objectKey);

    /**
     * Counts transcode tasks that are still reading the object.
     *
     * @param objectKey source object key to look for
     * @return number of queued or running tasks
     * @author Caspian
     * @since 0.17.0
     */
    @Select(
            "SELECT COUNT(*) FROM lesson_video_transcode "
                    + "WHERE source_object_key = #{objectKey} AND status IN (0, 1)")
    int countActiveTranscodes(@Param("objectKey") String objectKey);
}
