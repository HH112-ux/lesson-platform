package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.lp.course.entity.CourseLesson;
import com.lp.course.entity.LessonUpload;
import com.lp.course.mapper.LessonContentCleanupMapper;
import com.lp.course.mapper.LessonUploadMapper;
import com.lp.course.mapper.TeacherCourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies that a failed cleanup registration rolls back the claim that overwrote the lesson.
 *
 * <p>H2 rejects the MySQL multi-table {@code UPDATE} of the claim, so the mock performs the same
 * write through the real lesson mapper: both statements run inside the transaction opened by the
 * writer proxy, which is what the rollback assertion proves.
 *
 * @author Caspian
 * @since 0.16.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class LessonUploadConfirmRollbackTest {

    private static final String OLD_KEY = "lesson-videos/77/401.mp4";
    private static final String NEW_KEY = "lesson-videos/77/502.mp4";

    @Autowired private LessonUploadConfirmWriter writer;
    @Autowired private TeacherCourseMapper teacherCourseMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private LessonUploadMapper lessonUploadMapper;
    @MockitoBean private LessonContentCleanupMapper contentCleanupMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS course_lesson");
        jdbcTemplate.execute(
                "CREATE TABLE course_lesson (id BIGINT PRIMARY KEY, course_id BIGINT NOT NULL, "
                        + "chapter_id BIGINT NOT NULL, title VARCHAR(120) NOT NULL, "
                        + "video_url VARCHAR(500) NOT NULL, video_duration_sec INT NULL, "
                        + "video_codec VARCHAR(32) NULL, video_width INT NULL, video_height INT NULL, "
                        + "video_size_bytes BIGINT NULL, is_free TINYINT NULL, sort INT NULL, "
                        + "status TINYINT NULL, deleted TINYINT NOT NULL DEFAULT 0, "
                        + "version INT NOT NULL DEFAULT 0)");
        jdbcTemplate.update(
                "INSERT INTO course_lesson (id, course_id, chapter_id, title, video_url, status) "
                        + "VALUES (77, 9, 3, 'lesson', ?, 1)",
                OLD_KEY);
        when(lessonUploadMapper.confirm(
                        anyLong(),
                        any(),
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyInt(),
                        anyLong(),
                        anyString()))
                .thenAnswer(
                        invocation -> {
                            CourseLesson overwritten =
                                    teacherCourseMapper.selectLesson(9L, 3L, 77L);
                            overwritten.setVideoUrl(NEW_KEY);
                            return teacherCourseMapper.updateLesson(overwritten);
                        });
        when(contentCleanupMapper.insertObjectKeyCleanup(anyLong(), anyString()))
                .thenThrow(new DataIntegrityViolationException("registration failed"));
    }

    @Test
    void rollsBackTheClaimWhenRegistrationFails() {
        LessonVideoMetadata metadata =
                new LessonVideoMetadata("isom", 90.4, "h264", 1280, 720, "aac");
        LessonUpload session = new LessonUpload();
        session.setId(502L);
        session.setCourseId(9L);
        session.setChapterId(3L);
        session.setLessonId(77L);
        session.setObjectKey(NEW_KEY);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> writer.confirm(session, metadata, 1024L, "sha256-value"));
        assertEquals(
                OLD_KEY,
                jdbcTemplate.queryForObject(
                        "SELECT video_url FROM course_lesson WHERE id = 77", String.class));
    }
}
