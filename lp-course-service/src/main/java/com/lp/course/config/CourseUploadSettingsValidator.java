package com.lp.course.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Rejects upload settings that cannot work outside local development.
 *
 * <p>The dev profile may fall back to the MinIO credentials of {@code infra/docker-compose.yml}.
 * Every other profile must receive credentials through environment variables, so a deployment that
 * silently reuses a dev value fails at startup instead of signing URLs nobody can use. The size
 * ceiling mirrors {@code chk_lesson_upload_size} so configuration cannot drift above the database
 * constraint.
 *
 * @author Caspian
 * @since 0.7.0
 */
@Component
public class CourseUploadSettingsValidator implements InitializingBean {

    /** Local credentials of {@code application-dev.yml}, refused outside the dev profile. */
    private static final Set<String> DEV_CREDENTIALS = Set.of("lpminio", "lp_minio_dev");

    /**
     * Ceiling enforced by {@code chk_lesson_upload_size} in {@code
     * V9__lesson_upload_size_limit.sql}.
     */
    private static final long MAX_VIDEO_BYTES = 2_147_483_648L;

    /**
     * Lower bound of a presigned playback credential. Below one second the signed lifetime and the
     * reported expiry disagree, because presigning takes whole seconds.
     */
    private static final Duration MIN_PLAY_URL_TTL = Duration.ofSeconds(1);

    /**
     * Upper bound of a presigned playback credential. A longer lifetime turns the signed URL into a
     * reusable handle, which defeats the point of signing on every preview.
     */
    private static final Duration MAX_PLAY_URL_TTL = Duration.ofHours(1);

    private final CourseUploadProperties properties;

    private final Environment environment;

    /**
     * Creates the validator.
     *
     * @param properties upload settings to check
     * @param environment source of the active profiles
     * @author Caspian
     * @since 0.7.0
     */
    public CourseUploadSettingsValidator(
            CourseUploadProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Fails startup when the size ceiling, the playback lifetime or the credentials are unusable.
     *
     * @throws IllegalStateException when the size exceeds the database constraint, the playback
     *     lifetime is outside the accepted range, or a non-dev profile lacks real credentials
     * @author Caspian
     * @since 0.7.0
     */
    @Override
    public void afterPropertiesSet() {
        long maxSize = properties.maxSizeBytes();
        if (maxSize <= 0 || maxSize > MAX_VIDEO_BYTES) {
            throw new IllegalStateException(
                    "lp.course.upload.max-size-bytes must be between 1 and "
                            + MAX_VIDEO_BYTES
                            + " to match chk_lesson_upload_size");
        }
        Duration playUrlTtl = properties.playUrlTtl();
        if (playUrlTtl == null
                || playUrlTtl.compareTo(MIN_PLAY_URL_TTL) < 0
                || playUrlTtl.compareTo(MAX_PLAY_URL_TTL) > 0) {
            throw new IllegalStateException(
                    "lp.course.upload.play-url-ttl must be between 1s and "
                            + MAX_PLAY_URL_TTL
                            + " so a signed playback URL stays short lived");
        }
        if (List.of(environment.getActiveProfiles()).contains("dev")) {
            return;
        }
        if (isMissingOrDev(properties.minioAccessKey())
                || isMissingOrDev(properties.minioSecretKey())) {
            throw new IllegalStateException(
                    "lp.course.upload.minio-access-key and minio-secret-key must be injected through"
                            + " LP_COURSE_UPLOAD_MINIO_ACCESS_KEY and"
                            + " LP_COURSE_UPLOAD_MINIO_SECRET_KEY outside the dev profile");
        }
    }

    private boolean isMissingOrDev(String value) {
        return value == null || value.isBlank() || DEV_CREDENTIALS.contains(value);
    }
}
