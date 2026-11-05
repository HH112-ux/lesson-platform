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
}
