package com.lp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for WeChat miniapp code login.
 *
 * <p>The feature is off by default: with {@code enabled=false} the endpoint rejects every request
 * with {@code 66003} before any upstream call, so deploying without an AppSecret is safe. The
 * secret never enters the repository; it is supplied through {@code LP_MINIAPP_APP_SECRET}. The
 * {@code mock-enabled} flag swaps the real {@code jscode2session} client for a deterministic local
 * stand-in and must stay false outside local and test environments.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "lp.auth.miniapp")
public class MiniappProperties {

    /** Default WeChat API base address. */
    public static final String DEFAULT_BASE_URL = "https://api.weixin.qq.com";

    /** Default connect timeout in milliseconds. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;

    /** Default read timeout in milliseconds. */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;

    private boolean enabled;

    private boolean mockEnabled;

    private String appId = "";

    private String appSecret = "";

    private String baseUrl = DEFAULT_BASE_URL;

    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;

    private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;

    /**
     * Returns whether miniapp code login is enabled.
     *
     * @return {@code true} when the endpoint may exchange codes
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether miniapp code login is enabled.
     *
     * @param enabled feature switch
     * @author Caspian
     * @since 0.1.0
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns whether the deterministic mock client replaces the real one.
     *
     * @return {@code true} when the mock client is active
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isMockEnabled() {
        return mockEnabled;
    }

    /**
     * Sets whether the deterministic mock client replaces the real one.
     *
     * @param mockEnabled mock switch, local and test only
     * @author Caspian
     * @since 0.1.0
     */
    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    /**
     * Returns the WeChat miniapp AppID.
     *
     * @return app id, blank when not configured
     * @author Caspian
     * @since 0.1.0
     */
    public String getAppId() {
        return appId;
    }

    /**
     * Sets the WeChat miniapp AppID.
     *
     * @param appId WeChat app id
     * @author Caspian
     * @since 0.1.0
     */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    /**
     * Returns the WeChat miniapp AppSecret.
     *
     * @return app secret, blank when not configured
     * @author Caspian
     * @since 0.1.0
     */
    public String getAppSecret() {
        return appSecret;
    }

    /**
     * Sets the WeChat miniapp AppSecret.
     *
     * @param appSecret WeChat app secret, supplied only through the environment
     * @author Caspian
     * @since 0.1.0
     */
    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    /**
     * Returns the WeChat API base address.
     *
     * @return base url used by the real client
     * @author Caspian
     * @since 0.1.0
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the WeChat API base address.
     *
     * @param baseUrl base url used by the real client
     * @author Caspian
     * @since 0.1.0
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Returns the upstream connect timeout.
     *
     * @return timeout in milliseconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /**
     * Sets the upstream connect timeout.
     *
     * @param connectTimeoutMillis timeout in milliseconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    /**
     * Returns the upstream read timeout.
     *
     * @return timeout in milliseconds
     * @author Caspian
     * @since 0.1.0
     */
    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    /**
     * Sets the upstream read timeout.
     *
     * @param readTimeoutMillis timeout in milliseconds
     * @author Caspian
     * @since 0.1.0
     */
    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }
}
