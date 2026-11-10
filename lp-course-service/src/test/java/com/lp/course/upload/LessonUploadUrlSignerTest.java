package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lp.common.exception.BizException;
import com.lp.course.config.CourseUploadProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the presigned lesson upload credential.
 *
 * @author Caspian
 * @since 0.7.0
 */
class LessonUploadUrlSignerTest {

    private static final CourseUploadProperties CONFIGURED =
            new CourseUploadProperties(
                    Duration.ofMinutes(30),
                    Duration.ofMinutes(10),
                    209_715_200L,
                    "http://127.0.0.1:9000",
                    "us-east-1",
                    "test-access-key",
                    "test-secret-key",
                    "lp-media");

    @Test
    void signsPutUrlForThirtyMinutes() {
        String url =
                new LessonUploadUrlSigner(CONFIGURED)
                        .presignPut("lesson-videos/77/501.mp4", Duration.ofMinutes(30));

        assertTrue(url.startsWith("http://127.0.0.1:9000/lp-media/lesson-videos/77/501.mp4?"));
        assertTrue(url.contains("X-Amz-Expires=1800"));
        assertTrue(url.contains("X-Amz-Signature="));
    }

    @Test
    void signsPlaybackUrlForTheConfiguredLifetime() {
        String url =
                new LessonUploadUrlSigner(CONFIGURED)
                        .presignGet("lesson-videos/77/501.mp4", Duration.ofMinutes(10));

        assertTrue(url.startsWith("http://127.0.0.1:9000/lp-media/lesson-videos/77/501.mp4?"));
        assertTrue(url.contains("X-Amz-Expires=600") && url.contains("X-Amz-Signature="));
    }

    @Test
    void rejectsSigningWhenCredentialsAreMissing() {
        CourseUploadProperties blank =
                new CourseUploadProperties(
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(10),
                        209_715_200L,
                        "",
                        "",
                        "",
                        "",
                        "lp-media");

        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                new LessonUploadUrlSigner(blank)
                                        .presignPut(
                                                "lesson-videos/77/501.mp4",
                                                Duration.ofMinutes(30)));

        assertEquals(50206, exception.getCode());
    }
}
