package com.lp.learn.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime settings of the learn service.
 *
 * <p>The course-service address points at the internal read API only; it is never the public
 * gateway address.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "lp.learn")
public class LearnProperties {

    private String courseBaseUrl = "http://127.0.0.1:8102";

    private Duration courseReadTimeout = Duration.ofSeconds(2);

    private Duration playTokenTtl = Duration.ofMinutes(10);

    private String minioEndpoint = "http://127.0.0.1:9000";

    private String minioAccessKey = "";

    private String minioSecretKey = "";

    private String minioBucket = "lp-media";

    private boolean freePreviewEnabled = true;

    /**
     * Returns the base URL of the course-service internal API.
     *
     * @return scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public String getCourseBaseUrl() {
        return courseBaseUrl;
    }

    /**
     * Sets the base URL of the course-service internal API.
     *
     * @param courseBaseUrl scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseBaseUrl(String courseBaseUrl) {
        this.courseBaseUrl = courseBaseUrl;
    }

    /**
     * Returns the read timeout applied to course-service calls.
     *
     * @return read timeout, defaults to two seconds
     * @author Caspian
     * @since 0.1.0
     */
    public Duration getCourseReadTimeout() {
        return courseReadTimeout;
    }

    /**
     * Sets the read timeout applied to course-service calls.
     *
     * @param courseReadTimeout read timeout, must be positive
     * @author Caspian
     * @since 0.1.0
     */
    public void setCourseReadTimeout(Duration courseReadTimeout) {
        this.courseReadTimeout = courseReadTimeout;
    }

    /**
     * Returns the lifetime of a playback URL.
     *
     * @return presigned URL lifetime, defaults to ten minutes
     * @author Caspian
     * @since 0.1.0
     */
    public Duration getPlayTokenTtl() {
        return playTokenTtl;
    }

    /**
     * Sets the lifetime of a playback URL.
     *
     * @param playTokenTtl presigned URL lifetime, must be positive
     * @author Caspian
     * @since 0.1.0
     */
    public void setPlayTokenTtl(Duration playTokenTtl) {
        this.playTokenTtl = playTokenTtl;
    }

    /**
     * Returns the MinIO endpoint.
     *
     * @return endpoint URL
     * @author Caspian
     * @since 0.1.0
     */
    public String getMinioEndpoint() {
        return minioEndpoint;
    }

    /**
     * Sets the MinIO endpoint.
     *
     * @param minioEndpoint endpoint URL
     * @author Caspian
     * @since 0.1.0
     */
    public void setMinioEndpoint(String minioEndpoint) {
        this.minioEndpoint = minioEndpoint;
    }

    /**
     * Returns the MinIO access key.
     *
     * @return access key, empty when not configured
     * @author Caspian
     * @since 0.1.0
     */
    public String getMinioAccessKey() {
        return minioAccessKey;
    }

    /**
     * Sets the MinIO access key.
     *
     * @param minioAccessKey access key, empty when unknown
     * @author Caspian
     * @since 0.1.0
     */
    public void setMinioAccessKey(String minioAccessKey) {
        this.minioAccessKey = minioAccessKey;
    }

    /**
     * Returns the MinIO secret key.
     *
     * @return secret key, empty when not configured
     * @author Caspian
     * @since 0.1.0
     */
    public String getMinioSecretKey() {
        return minioSecretKey;
    }

    /**
     * Sets the MinIO secret key.
     *
     * @param minioSecretKey secret key, empty when unknown
     * @author Caspian
     * @since 0.1.0
     */
    public void setMinioSecretKey(String minioSecretKey) {
        this.minioSecretKey = minioSecretKey;
    }

    /**
     * Returns the bucket holding lesson videos.
     *
     * @return bucket name
     * @author Caspian
     * @since 0.1.0
     */
    public String getMinioBucket() {
        return minioBucket;
    }

    /**
     * Sets the bucket holding lesson videos.
     *
     * @param minioBucket bucket name
     * @author Caspian
     * @since 0.1.0
     */
    public void setMinioBucket(String minioBucket) {
        this.minioBucket = minioBucket;
    }

    /**
     * Tells whether free trial lessons can be opened without a purchase.
     *
     * @return {@code true} by default; {@code false} makes every lesson entitlement-only
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isFreePreviewEnabled() {
        return freePreviewEnabled;
    }

    /**
     * Sets the free trial switch.
     *
     * @param freePreviewEnabled {@code false} to require an entitlement for free lessons too
     * @author Caspian
     * @since 0.1.0
     */
    public void setFreePreviewEnabled(boolean freePreviewEnabled) {
        this.freePreviewEnabled = freePreviewEnabled;
    }
}
