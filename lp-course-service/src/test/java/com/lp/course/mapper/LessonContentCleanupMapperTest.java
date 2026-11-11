package com.lp.course.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        jdbcTemplate.execute("DROP TABLE IF EXISTS lesson_content_cleanup");
        jdbcTemplate.execute("DROP TABLE IF EXISTS course_lesson");
        jdbcTemplate.execute(
                "CREATE TABLE course_lesson (id BIGINT PRIMARY KEY, course_id BIGINT NOT NULL, "
                        + "chapter_id BIGINT NOT NULL, video_url VARCHAR(500) NOT NULL, "
                        + "deleted TINYINT NOT NULL DEFAULT 0)");
        jdbcTemplate.execute(
                "CREATE TABLE lesson_content_cleanup (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "course_id BIGINT NOT NULL, object_key VARCHAR(500) NOT NULL, "
                        + "status TINYINT NOT NULL DEFAULT 0, attempt_count TINYINT NOT NULL "
                        + "DEFAULT 0, next_retry_at DATETIME NULL, last_error VARCHAR(255) NULL, "
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
