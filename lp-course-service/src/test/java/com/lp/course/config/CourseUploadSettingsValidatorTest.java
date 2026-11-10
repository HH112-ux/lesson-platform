package com.lp.course.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for the upload settings guard.
 *
 * @author Caspian
 * @since 0.7.0
 */
class CourseUploadSettingsValidatorTest {

    @Test
    void acceptsDevCredentialsInDevProfile() {
        assertDoesNotThrow(
                () ->
                        new CourseUploadSettingsValidator(
                                        properties(209_715_200L, "lpminio", "lp_minio_dev"),
                                        environment("dev"))
                                .afterPropertiesSet());
    }

    @Test
    void rejectsDevCredentialsOutsideDevProfile() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new CourseUploadSettingsValidator(
                                                properties(209_715_200L, "lpminio", "lp_minio_dev"),
                                                environment("prod"))
                                        .afterPropertiesSet());

        assertTrue(exception.getMessage().contains("LP_COURSE_UPLOAD_MINIO_ACCESS_KEY"));
    }

    @Test
    void rejectsMissingCredentialsOutsideDevProfile() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new CourseUploadSettingsValidator(
                                        properties(209_715_200L, "", ""), environment("staging"))
                                .afterPropertiesSet());
    }

    @Test
    void rejectsSizeAboveDatabaseConstraint() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new CourseUploadSettingsValidator(
                                        properties(2_147_483_649L, "real-key", "real-secret"),
                                        environment("dev"))
                                .afterPropertiesSet());
    }

    @Test
    void rejectsPlayUrlTtlAboveOneHour() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                new CourseUploadSettingsValidator(
                                                properties(
                                                        209_715_200L,
                                                        "real-key",
                                                        "real-secret",
                                                        Duration.ofHours(2)),
                                                environment("dev"))
                                        .afterPropertiesSet());

        assertTrue(exception.getMessage().contains("play-url-ttl"));
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    private CourseUploadProperties properties(long maxSize, String accessKey, String secretKey) {
        return properties(maxSize, accessKey, secretKey, Duration.ofMinutes(10));
    }

    private CourseUploadProperties properties(
            long maxSize, String accessKey, String secretKey, Duration playUrlTtl) {
        return new CourseUploadProperties(
                Duration.ofMinutes(30),
                playUrlTtl,
                maxSize,
                "http://127.0.0.1:9000",
                "us-east-1",
                accessKey,
                secretKey,
                "lp-media");
    }
}
