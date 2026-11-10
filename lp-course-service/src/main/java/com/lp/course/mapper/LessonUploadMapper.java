package com.lp.course.mapper;

import com.lp.course.entity.LessonUpload;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Creates and reads presigned lesson upload sessions stored in {@code lesson_upload}.
 *
 * <p>The generated {@code pending_flag} column is never written here: the unique key {@code
 * uk_lesson_pending} derives it from {@code status}, so a second pending insert for the same lesson
 * fails with {@code DuplicateKeyException} until the previous session leaves status 0. Callers are
 * expected to catch that conflict and read the winning row back instead of failing the request.
 * Timestamps default in the database, so inserts leave {@code created_at} and {@code updated_at}
 * unset.
 *
 * @author Caspian
 * @since 0.7.0
 */
@Mapper
public interface LessonUploadMapper {

    /** Column list shared by the read statements. */
    String COLUMNS =
            "id, course_id AS courseId, chapter_id AS chapterId, lesson_id AS lessonId, "
                    + "uploader_id AS uploaderId, object_key AS objectKey, "
                    + "content_type AS contentType, expected_size_bytes AS expectedSizeBytes, "
                    + "status, session_expires_at AS sessionExpiresAt, "
                    + "confirmed_at AS confirmedAt, created_at AS createdAt, updated_at AS updatedAt";

    /**
     * Inserts one pending upload session.
     *
     * @param upload session to insert, filled with the generated id when inserted
     * @return affected rows, always 1 when the insert succeeds
     * @author Caspian
     * @since 0.7.0
     */
    @Insert(
            "INSERT INTO lesson_upload (course_id, chapter_id, lesson_id, uploader_id, object_key, "
                    + "content_type, expected_size_bytes, status, session_expires_at) "
                    + "VALUES (#{courseId}, #{chapterId}, #{lessonId}, #{uploaderId}, #{objectKey}, "
                    + "#{contentType}, #{expectedSizeBytes}, #{status}, #{sessionExpiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(LessonUpload upload);

    /**
     * Reads one session by primary key.
     *
     * @param id session primary key
     * @return matching session, {@code null} when absent
     * @author Caspian
     * @since 0.7.0
     */
    @Select("SELECT " + COLUMNS + " FROM lesson_upload WHERE id = #{id}")
    LessonUpload selectById(@Param("id") long id);

    /**
     * Reads the pending session of one lesson.
     *
     * @param lessonId lesson primary key
     * @return the session whose status is 0, {@code null} when the lesson has none
     * @author Caspian
     * @since 0.7.0
     */
    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM lesson_upload "
                    + "WHERE lesson_id = #{lessonId} AND pending_flag = 1")
    LessonUpload selectPendingByLessonId(@Param("lessonId") long lessonId);

    /**
     * Writes the final object key once the insert has generated the session id.
     *
     * @param id session primary key
     * @param objectKey stable key built from the lesson id and the session id
     * @return affected rows
     * @author Caspian
     * @since 0.7.0
     */
    @Update("UPDATE lesson_upload SET object_key = #{objectKey} WHERE id = #{id}")
    int updateObjectKey(@Param("id") long id, @Param("objectKey") String objectKey);

    /**
     * Releases the pending slot of a session whose credential outlived its expiry.
     *
     * <p>The generated {@code pending_flag} follows {@code status}, so setting status 2 frees
     * {@code uk_lesson_pending} for a new session without touching the generated column.
     *
     * @param id session primary key
     * @return affected rows, 0 when the row already left status 0
     * @author Caspian
     * @since 0.7.0
     */
    @Update("UPDATE lesson_upload SET status = 2 WHERE id = #{id} AND status = 0")
    int markExpired(@Param("id") long id);

    /**
     * Confirms a pending session and writes the lesson video facts in one statement.
     *
     * <p>The {@code u.status = 0} predicate makes this update the point where concurrent
     * confirmations race: exactly one caller claims the session, the others match no row and read
     * the winning state instead. A missing or deleted lesson also matches no row.
     *
     * @param uploadId session primary key
     * @param confirmedAt confirmation timestamp
     * @param videoUrl stored object key
     * @param durationSec probed duration in seconds
     * @param codec probed video codec
     * @param width probed width in pixels
     * @param height probed height in pixels
     * @param sizeBytes stored object size in bytes
     * @param sha256 lower case hex digest of the stored object
     * @return affected rows, {@code 0} when the session already left status 0 or the lesson is gone
     * @author Caspian
     * @since 0.8.0
     */
    @Update(
            "UPDATE lesson_upload u JOIN course_lesson l ON l.id = u.lesson_id "
                    + "SET u.status = 1, u.confirmed_at = #{confirmedAt}, "
                    + "l.video_url = #{videoUrl}, l.video_duration_sec = #{durationSec}, "
                    + "l.video_codec = #{codec}, l.video_width = #{width}, "
                    + "l.video_height = #{height}, l.video_size_bytes = #{sizeBytes}, "
                    + "l.video_sha256 = #{sha256}, l.version = l.version + 1 "
                    + "WHERE u.id = #{uploadId} AND u.status = 0 AND l.deleted = 0")
    int confirm(
            @Param("uploadId") long uploadId,
            @Param("confirmedAt") LocalDateTime confirmedAt,
            @Param("videoUrl") String videoUrl,
            @Param("durationSec") int durationSec,
            @Param("codec") String codec,
            @Param("width") int width,
            @Param("height") int height,
            @Param("sizeBytes") long sizeBytes,
            @Param("sha256") String sha256);
}
