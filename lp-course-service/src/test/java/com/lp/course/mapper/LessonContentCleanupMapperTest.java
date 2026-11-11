package com.lp.course.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lp.course.cleanup.LessonCleanupRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the cleanup registration statements against an in-memory H2 database.
 *
 * <p>The blank key guard lives in SQL, because a lesson that never confirmed an upload stores an
 * empty {@code video_url} and the unique key on {@code object_key} would reject the second blank
 * row. Both registration paths are exercised here: the single lesson statement and the chapter
 * batch statement.
 *
 * <p>The queue statements of the worker are exercised too: batch selection including the record
 * result mapping, claim, defer, retry, both terminal states, stale reclaim and the three reference
 * counts. The worker tests mock this mapper, so these are the only tests that execute the SQL.
 *
 * @author Caspian
 * @since 0.16.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class LessonContentCleanupMapperTest {

    @Autowired private LessonContentCleanupMapper mapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * Recreates the two tables involved by the registration statements.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS lesson_video_transcode");
        jdbcTemplate.execute("DROP TABLE IF EXISTS lesson_upload");
        jdbcTemplate.execute("DROP TABLE IF EXISTS lesson_content_cleanup");
        jdbcTemplate.execute("DROP TABLE IF EXISTS course_lesson");
        jdbcTemplate.execute(
                "CREATE TABLE course_lesson (id BIGINT PRIMARY KEY, course_id BIGINT NOT NULL, "
                        + "chapter_id BIGINT NOT NULL, video_url VARCHAR(500) NOT NULL, "
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE lesson_upload (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "lesson_id BIGINT NOT NULL, object_key VARCHAR(500) NOT NULL, "
                        + "status TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE lesson_video_transcode (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "lesson_id BIGINT NOT NULL, source_object_key VARCHAR(500) NOT NULL, "
                        + "status TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE lesson_content_cleanup (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "course_id BIGINT NOT NULL, object_key VARCHAR(500) NOT NULL, "
                        + "status TINYINT NOT NULL DEFAULT 0, attempt_count TINYINT NOT NULL "
                        + "DEFAULT 0, next_retry_at DATETIME NULL, last_error VARCHAR(255) NULL, "
                        + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "CONSTRAINT uk_object_key UNIQUE (object_key))");
    }

    /**
     * A blank key is not registered and an empty video does not fail the statement.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void skipsBlankKeyOfOneLesson() {
        insertLesson(81L, 9L, 71L, "", 0);
        insertLesson(82L, 9L, 71L, "   ", 0);

        assertEquals(0, mapper.insertLessonCleanup(9L, 71L, 81L));
        assertEquals(0, mapper.insertLessonCleanup(9L, 71L, 82L));
        assertEquals(0, countRegistrations());
    }

    /**
     * A confirmed lesson is registered with its source key and course id.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void registersSourceKeyOfOneLesson() {
        insertLesson(83L, 9L, 71L, "videos/9/83.mp4", 0);

        assertEquals(1, mapper.insertLessonCleanup(9L, 71L, 83L));
        assertEquals("videos/9/83.mp4", objectKeyOfOnlyRegistration());
    }

    /**
     * The chapter batch registers confirmed keys only and ignores blank ones.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void registersOnlyNonBlankKeysOfChapter() {
        insertLesson(84L, 9L, 71L, "", 0);
        insertLesson(85L, 9L, 71L, "videos/9/85.mp4", 0);
        insertLesson(86L, 9L, 71L, "   ", 0);
        insertLesson(87L, 9L, 71L, "videos/9/87.mp4", 1);

        assertEquals(1, mapper.insertChapterLessonsCleanup(9L, 71L));
        assertEquals("videos/9/85.mp4", objectKeyOfOnlyRegistration());
    }

    /**
     * A repeated registration keeps one row instead of failing the delete transaction.
     *
     * @author Caspian
     * @since 0.16.0
     */
    @Test
    void registersOneRowPerObjectKey() {
        insertLesson(88L, 9L, 71L, "videos/9/88.mp4", 0);

        assertEquals(1, mapper.insertLessonCleanup(9L, 71L, 88L));
        assertEquals(0, mapper.insertLessonCleanup(9L, 71L, 88L));
        assertEquals(1, countRegistrations());
    }

    /**
     * Reads only due pending rows, in primary key order, with the record columns mapped.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void selectsOnlyDuePendingRowsOrderedById() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 11, 12, 0);
        insertCleanup(2L, "k2", 0, 0, now.minusMinutes(5), null);
        insertCleanup(1L, "k1", 0, 3, null, null);
        insertCleanup(3L, "k3", 0, 1, now.plusMinutes(5), null);
        insertCleanup(4L, "k4", 1, 0, null, null);

        List<LessonCleanupRow> rows = mapper.selectPending(now, 10);

        assertEquals(List.of(1L, 2L), rows.stream().map(LessonCleanupRow::id).toList());
        assertEquals("k1", rows.get(0).objectKey());
        assertEquals(3, rows.get(0).attemptCount());
    }

    /**
     * Claiming succeeds once and never touches a row another tick already moved.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void claimsOnlyPendingRow() {
        insertCleanup(1L, "k1", 0, 0, null, null);
        insertCleanup(2L, "k2", 1, 0, null, null);

        assertEquals(1, mapper.claim(1L));
        assertEquals(0, mapper.claim(1L));
        assertEquals(0, mapper.claim(2L));
    }

    /**
     * Deferring returns the row to pending without spending an attempt or keeping the error.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void defersWithoutSpendingAttempt() {
        LocalDateTime next = LocalDateTime.of(2026, 11, 11, 12, 30);
        insertCleanup(1L, "k1", 1, 2, null, "timeout");

        assertEquals(1, mapper.defer(1L, next));

        assertEquals(0, statusOf(1L));
        assertEquals(2, attemptsOf(1L));
        assertNull(lastErrorOf(1L));
        assertEquals(next, nextRetryAtOf(1L));
    }

    /**
     * A transient failure stores the incremented attempt count and the operator message.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void schedulesRetryWithAttemptsAndError() {
        LocalDateTime next = LocalDateTime.of(2026, 11, 11, 12, 30);
        insertCleanup(1L, "k1", 1, 0, null, null);

        assertEquals(1, mapper.scheduleRetry(1L, 1, next, "timeout"));

        assertEquals(0, statusOf(1L));
        assertEquals(1, attemptsOf(1L));
        assertEquals("timeout", lastErrorOf(1L));
        assertEquals(next, nextRetryAtOf(1L));
    }

    /**
     * Success moves a claimed row to done and clears the previous error.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void marksDone() {
        insertCleanup(1L, "k1", 1, 0, null, "timeout");

        assertEquals(1, mapper.markDone(1L));

        assertEquals(2, statusOf(1L));
        assertNull(lastErrorOf(1L));
        assertEquals(0, mapper.markDone(1L));
    }

    /**
     * Exhausting the retry budget stores the terminal state for a human to inspect.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void marksFailed() {
        insertCleanup(1L, "k1", 1, 4, null, null);

        assertEquals(1, mapper.markFailed(1L, 5, "AccessDenied"));

        assertEquals(3, statusOf(1L));
        assertEquals(5, attemptsOf(1L));
        assertEquals("AccessDenied", lastErrorOf(1L));
    }

    /**
     * Only rows left running by a crashed tick are reclaimed, and doing so spends an attempt.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void reclaimsOnlyStaleRunningRows() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 11, 12, 0);
        insertCleanup(1L, "stale", 1, 0, null, null, now.minusHours(1), now.minusHours(1));
        insertCleanup(2L, "fresh", 1, 0, null, null, now, now);
        insertCleanup(3L, "pending", 0, 0, null, null, now.minusHours(1), now.minusHours(1));

        assertEquals(1, mapper.reclaimStale(now.minusMinutes(10)));

        assertEquals(0, statusOf(1L));
        assertEquals(1, attemptsOf(1L));
        assertEquals(1, statusOf(2L));
        assertEquals(0, statusOf(3L));
    }

    /**
     * The three reference counts ignore deleted lessons, confirmed uploads and finished transcodes.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void countsOnlyLiveReferences() {
        insertLesson(1L, 9L, 71L, "shared.mp4", 0);
        insertLesson(2L, 9L, 71L, "shared.mp4", 1);
        jdbcTemplate.update(
                "INSERT INTO lesson_upload (lesson_id, object_key, status) VALUES (1, ?, 0)",
                "shared.mp4");
        jdbcTemplate.update(
                "INSERT INTO lesson_upload (lesson_id, object_key, status) VALUES (2, ?, 1)",
                "shared.mp4");
        jdbcTemplate.update(
                "INSERT INTO lesson_video_transcode (lesson_id, source_object_key, status) "
                        + "VALUES (1, ?, 1)",
                "shared.mp4");
        jdbcTemplate.update(
                "INSERT INTO lesson_video_transcode (lesson_id, source_object_key, status) "
                        + "VALUES (2, ?, 2)",
                "shared.mp4");

        assertEquals(1, mapper.countLessonReferences("shared.mp4"));
        assertEquals(1, mapper.countPendingUploads("shared.mp4"));
        assertEquals(1, mapper.countActiveTranscodes("shared.mp4"));
    }

    private void insertCleanup(
            long id,
            String objectKey,
            int status,
            int attempts,
            LocalDateTime nextRetryAt,
            String lastError) {
        insertCleanup(
                id,
                objectKey,
                status,
                attempts,
                nextRetryAt,
                lastError,
                LocalDateTime.of(2026, 11, 11, 10, 0),
                LocalDateTime.of(2026, 11, 11, 10, 0));
    }

    private void insertCleanup(
            long id,
            String objectKey,
            int status,
            int attempts,
            LocalDateTime nextRetryAt,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        jdbcTemplate.update(
                "INSERT INTO lesson_content_cleanup (id, course_id, object_key, status, "
                        + "attempt_count, next_retry_at, last_error, created_at, updated_at) "
                        + "VALUES (?, 9, ?, ?, ?, ?, ?, ?, ?)",
                id,
                objectKey,
                status,
                attempts,
                nextRetryAt,
                lastError,
                createdAt,
                updatedAt);
    }

    private int statusOf(long id) {
        Integer value =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM lesson_content_cleanup WHERE id = ?",
                        Integer.class,
                        id);
        return value == null ? -1 : value;
    }

    private int attemptsOf(long id) {
        Integer value =
                jdbcTemplate.queryForObject(
                        "SELECT attempt_count FROM lesson_content_cleanup WHERE id = ?",
                        Integer.class,
                        id);
        return value == null ? -1 : value;
    }

    private String lastErrorOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT last_error FROM lesson_content_cleanup WHERE id = ?", String.class, id);
    }

    private LocalDateTime nextRetryAtOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM lesson_content_cleanup WHERE id = ?",
                LocalDateTime.class,
                id);
    }

    private void insertLesson(
            long id, long courseId, long chapterId, String videoUrl, int deleted) {
        jdbcTemplate.update(
                "INSERT INTO course_lesson (id, course_id, chapter_id, video_url, deleted) "
                        + "VALUES (?, ?, ?, ?, ?)",
                id,
                courseId,
                chapterId,
                videoUrl,
                deleted);
    }

    private long countRegistrations() {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM lesson_content_cleanup", Long.class);
        return count == null ? 0 : count;
    }

    private String objectKeyOfOnlyRegistration() {
        return jdbcTemplate.queryForObject(
                "SELECT object_key FROM lesson_content_cleanup", String.class);
    }
}
