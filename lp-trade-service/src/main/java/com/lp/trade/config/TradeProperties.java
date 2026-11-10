package com.lp.trade.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime settings of the trade service.
 *
 * <p>The course-service address points at the internal read API only; it is never the public
 * gateway address.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "lp.trade")
public class TradeProperties {

    private String courseBaseUrl = "http://127.0.0.1:8102";

    private Duration courseReadTimeout = Duration.ofSeconds(2);

    private String learnBaseUrl = "http://127.0.0.1:8104";

    private Duration learnReadTimeout = Duration.ofSeconds(2);

    private int outboxMaxRetries = 10;

    private String webBaseUrl = "http://127.0.0.1:5173";

    private String authBaseUrl = "http://127.0.0.1:8101";

    private Duration authStateReadTimeout = Duration.ofMillis(200);

    private Duration accountGuardCacheTtl = Duration.ofSeconds(45);

    /**
     * Returns the browser base URL used in purchase ticket links.
     *
     * @return scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    /**
     * Sets the browser base URL used in purchase ticket links.
     *
     * @param webBaseUrl scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public void setWebBaseUrl(String webBaseUrl) {
        this.webBaseUrl = webBaseUrl;
    }

    /**
     * Returns the base URL of the auth-service internal API.
     *
     * @return scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    /**
     * Sets the base URL of the auth-service internal API.
     *
     * @param authBaseUrl scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }

    /**
     * Returns the read timeout applied to the cooling-off state query.
     *
     * <p>The query sits on the checkout path, so a slow auth-service must be reported quickly and
     * fail closed rather than delay the buyer.
     *
     * @return read timeout, defaults to 200 milliseconds
     * @author Caspian
     * @since 0.3.0
     */
    public Duration getAuthStateReadTimeout() {
        return authStateReadTimeout;
    }

    /**
     * Sets the read timeout applied to the cooling-off state query.
     *
     * @param authStateReadTimeout read timeout, must be positive
     * @author Caspian
     * @since 0.3.0
     */
    public void setAuthStateReadTimeout(Duration authStateReadTimeout) {
        this.authStateReadTimeout = authStateReadTimeout;
    }

    /**
     * Returns how long a cooling-off answer stays cached per user.
     *
     * <p>This is also the worst case window when the active invalidation call fails, which is why
     * it is short enough to bound a mistake but long enough that a repeated failure does not turn
     * into four probes per minute per buyer.
     *
     * @return cache TTL, defaults to 45 seconds
     * @author Caspian
     * @since 0.3.0
     */
    public Duration getAccountGuardCacheTtl() {
        return accountGuardCacheTtl;
    }

    /**
     * Sets how long a cooling-off answer stays cached per user.
     *
     * @param accountGuardCacheTtl cache TTL, must be positive
     * @author Caspian
     * @since 0.3.0
     */
    public void setAccountGuardCacheTtl(Duration accountGuardCacheTtl) {
        this.accountGuardCacheTtl = accountGuardCacheTtl;
    }

    /**
     * Returns the base URL of the learn-service internal API.
     *
     * @return scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public String getLearnBaseUrl() {
        return learnBaseUrl;
    }

    /**
     * Sets the base URL of the learn-service internal API.
     *
     * @param learnBaseUrl scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.1.0
     */
    public void setLearnBaseUrl(String learnBaseUrl) {
        this.learnBaseUrl = learnBaseUrl;
    }

    /**
     * Returns the read timeout applied to learn-service calls.
     *
     * @return read timeout, defaults to two seconds
     * @author Caspian
     * @since 0.1.0
     */
    public Duration getLearnReadTimeout() {
        return learnReadTimeout;
    }

    /**
     * Sets the read timeout applied to learn-service calls.
     *
     * @param learnReadTimeout read timeout, must be positive
     * @author Caspian
     * @since 0.1.0
     */
    public void setLearnReadTimeout(Duration learnReadTimeout) {
        this.learnReadTimeout = learnReadTimeout;
    }

    /**
     * Returns how many delivery attempts the outbox relay makes before giving up.
     *
     * @return attempt limit, defaults to ten
     * @author Caspian
     * @since 0.1.0
     */
    public int getOutboxMaxRetries() {
        return outboxMaxRetries;
    }

    /**
     * Sets how many delivery attempts the outbox relay makes before giving up.
     *
     * @param outboxMaxRetries attempt limit, must be at least one
     * @author Caspian
     * @since 0.1.0
     */
    public void setOutboxMaxRetries(int outboxMaxRetries) {
        this.outboxMaxRetries = outboxMaxRetries;
    }

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
