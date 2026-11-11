package com.lp.course.mapper;

import com.lp.course.entity.LessonVideoTranscode;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Records and advances asynchronous lesson video transcode tasks.
 *
 * <p>The unique key {@code uk_source_object} makes {@link #insertIgnore} the idempotency gate: a
 * repeated confirmation of the same source object inserts no second row. Status transitions use
 * conditional updates so a duplicate worker can never move a task backwards.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Mapper
public interface LessonVideoTranscodeMapper {

    /** Column list shared by the read statements. */
    String COLUMNS =
            "id, lesson_id AS lessonId, upload_id AS uploadId, "
                    + "source_object_key AS sourceObjectKey, "
                    + "source_size_bytes AS sourceSizeBytes, "
                    + "output_object_key AS outputObjectKey, status, "
                    + "attempt_count AS attemptCount, failure_reason AS failureReason, "
                    + "started_at AS startedAt, finished_at AS finishedAt, "
                    + "created_at AS createdAt, updated_at AS updatedAt";

    /**
     * Inserts one pending task unless the source object already has a row.
     *
     * <p>Uses a no-op {@code ON DUPLICATE KEY UPDATE} like {@code AdminAuditLogMapper}, so only a
     * duplicate key is absorbed; a genuine insert error still fails the confirmation.
     *
     * @param task task to insert, filled with the generated id when inserted
     * @return affected rows, {@code 0} when the source object is already recorded
     * @author Caspian
     * @since 0.11.0
     */
    @Insert(
            "INSERT INTO lesson_video_transcode "
                    + "(lesson_id, upload_id, source_object_key, source_size_bytes, status) "
                    + "VALUES (#{lessonId}, #{uploadId}, #{sourceObjectKey}, "
                    + "#{sourceSizeBytes}, #{status}) "
                    + "ON DUPLICATE KEY UPDATE source_object_key = source_object_key")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertIfAbsent(LessonVideoTranscode task);

    /**
     * Reads the task of one source object.
     *
     * @param sourceObjectKey stored source object key
     * @return matching task, {@code null} when the object was never queued
     * @author Caspian
     * @since 0.11.0
     */
    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM lesson_video_transcode WHERE source_object_key = #{sourceObjectKey}")
    LessonVideoTranscode selectBySourceObjectKey(@Param("sourceObjectKey") String sourceObjectKey);

    /**
     * Reads one task by id.
     *
     * @param id task primary key
     * @return matching task, {@code null} when it does not exist
     * @author Caspian
     * @since 0.11.0
     */
    @Select("SELECT " + COLUMNS + " FROM lesson_video_transcode WHERE id = #{id}")
    LessonVideoTranscode selectById(@Param("id") long id);

    /**
     * Marks a pending or running task as running and records the attempt number.
     *
     * <p>READY and FAILED rows are left untouched, so neither a duplicate worker nor a late submit
     * can restart a finished task. {@code started_at} keeps the first start time across retries.
     *
     * @param id task primary key
     * @param attempt one based attempt number
     * @param startedAt time this attempt started
     * @return affected rows, {@code 0} when the task is READY or FAILED
     * @author Caspian
     * @since 0.11.0
     */
    @Update(
            "UPDATE lesson_video_transcode "
                    + "SET status = 1, attempt_count = #{attempt}, "
                    + "started_at = COALESCE(started_at, #{startedAt}), failure_reason = NULL "
                    + "WHERE id = #{id} AND status IN (0, 1)")
    int markRunning(
            @Param("id") long id,
            @Param("attempt") int attempt,
            @Param("startedAt") LocalDateTime startedAt);

    /**
     * Marks a running task as ready and publishes the output object key.
     *
     * @param id task primary key
     * @param outputObjectKey transcoded object key under the {@code transcoded/} prefix
     * @param finishedAt time the output was stored
     * @return affected rows, {@code 0} when the task is not RUNNING
     * @author Caspian
     * @since 0.11.0
     */
    @Update(
            "UPDATE lesson_video_transcode "
                    + "SET status = 2, output_object_key = #{outputObjectKey}, "
                    + "failure_reason = NULL, finished_at = #{finishedAt} "
                    + "WHERE id = #{id} AND status = 1")
    int markReady(
            @Param("id") long id,
            @Param("outputObjectKey") String outputObjectKey,
            @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * Marks a pending or running task as finally failed.
     *
     * <p>Pending rows are included so a task rejected by a full executor queue can be closed
     * without ever reaching RUNNING.
     *
     * @param id task primary key
     * @param failureReason last failure reason, truncated to the column length by the caller
     * @param finishedAt time the last attempt gave up
     * @return affected rows, {@code 0} when the task is READY
     * @author Caspian
     * @since 0.11.0
     */
    @Update(
            "UPDATE lesson_video_transcode "
                    + "SET status = 3, failure_reason = #{failureReason}, "
                    + "finished_at = #{finishedAt} "
                    + "WHERE id = #{id} AND status IN (0, 1)")
    int markFailed(
            @Param("id") long id,
            @Param("failureReason") String failureReason,
            @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * List ids of tasks never picked up by a worker.
     *
     * <p>Used by startup recovery to resubmit tasks whose submission was lost by a crash.
     *
     * @return pending task ids in creation order
     * @author Caspian
     * @since 0.11.0
     */
    @Select("SELECT id FROM lesson_video_transcode WHERE status = 0 ORDER BY id")
    List<Long> selectPendingIds();

    /**
     * List ids of tasks left RUNNING by a previous process.
     *
     * @return running task ids in creation order
     * @author Caspian
     * @since 0.11.0
     */
    @Select("SELECT id FROM lesson_video_transcode WHERE status = 1 ORDER BY id")
    List<Long> selectRunningIds();

    /**
     * Moves an interrupted task back to PENDING and counts the interrupted run as an attempt.
     *
     * @param id task primary key
     * @param attempt attempts started so far, including the interrupted one
     * @return affected rows, {@code 0} when the task is no longer RUNNING
     * @author Caspian
     * @since 0.11.0
     */
    @Update(
            "UPDATE lesson_video_transcode "
                    + "SET status = 0, attempt_count = #{attempt}, "
                    + "failure_reason = NULL, started_at = NULL "
                    + "WHERE id = #{id} AND status = 1")
    int requeueAfterRestart(@Param("id") long id, @Param("attempt") int attempt);
}
