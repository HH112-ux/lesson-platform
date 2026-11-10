package com.lp.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the account cancellation feature.
 *
 * <p>The trade address points at the trade-service internal API used to probe blockers; it is never
 * the public gateway address. The read timeout is deliberately short because the probe runs inside
 * a user facing request and must fail closed rather than hang.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ConfigurationProperties(prefix = "lp.auth.account-deletion")
public class AccountDeletionProperties {

    private String tradeBaseUrl = "http://127.0.0.1:8103";

    private Duration tradeReadTimeout = Duration.ofSeconds(2);

    private Duration coolingOffPeriod = Duration.ofDays(7);

    private Duration cacheInvalidateReadTimeout = Duration.ofMillis(200);

    private String anonymizeSalt = "";

    /**
     * Returns the secret salt mixed into every anonymisation digest.
     *
     * <p>One value is used for both sides: auth-service hashes the WeChat openid and passes the
     * same salt to the trade service, so rotating it only ever affects rows not yet anonymised.
     *
     * @return configured salt, empty when the operator never supplied it
     * @author Caspian
     * @since 0.3.0
     */
    public String getAnonymizeSalt() {
        return anonymizeSalt;
    }

    /**
     * Sets the secret salt mixed into every anonymisation digest.
     *
     * @param anonymizeSalt salt from {@code LP_ANONYMIZE_SALT}
     * @author Caspian
     * @since 0.3.0
     */
    public void setAnonymizeSalt(String anonymizeSalt) {
        this.anonymizeSalt = anonymizeSalt;
    }

    /**
     * Returns the base URL of the trade-service internal API.
     *
     * @return scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.2.0
     */
    public String getTradeBaseUrl() {
        return tradeBaseUrl;
    }

    /**
     * Sets the base URL of the trade-service internal API.
     *
     * @param tradeBaseUrl scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.2.0
     */
    public void setTradeBaseUrl(String tradeBaseUrl) {
        this.tradeBaseUrl = tradeBaseUrl;
    }

    /**
     * Returns the read timeout applied to blocker probes.
     *
     * @return read timeout, defaults to two seconds
     * @author Caspian
     * @since 0.2.0
     */
    public Duration getTradeReadTimeout() {
        return tradeReadTimeout;
    }

    /**
     * Sets the read timeout applied to blocker probes.
     *
     * @param tradeReadTimeout read timeout, must be positive
     * @author Caspian
     * @since 0.2.0
     */
    public void setTradeReadTimeout(Duration tradeReadTimeout) {
        this.tradeReadTimeout = tradeReadTimeout;
    }

    /**
     * Returns the cooling-off period applied when a cancellation is requested.
     *
     * <p>The value is only read while creating a request; the resulting {@code effective_at} is
     * stored, so changing this setting never moves a promise already made to a user.
     *
     * @return cooling-off period, defaults to seven days
     * @author Caspian
     * @since 0.2.0
     */
    public Duration getCoolingOffPeriod() {
        return coolingOffPeriod;
    }

    /**
     * Sets the cooling-off period applied when a cancellation is requested.
     *
     * @param coolingOffPeriod cooling-off period, must be positive
     * @author Caspian
     * @since 0.2.0
     */
    public void setCoolingOffPeriod(Duration coolingOffPeriod) {
        this.coolingOffPeriod = coolingOffPeriod;
    }

    /**
     * Returns the read timeout applied to trade cache invalidation calls.
     *
     * <p>The call runs off the request thread, so the timeout bounds only how long a background
     * worker waits; it never adds latency to the user's response.
     *
     * @return read timeout, defaults to 200 milliseconds
     * @author Caspian
     * @since 0.3.0
     */
    public Duration getCacheInvalidateReadTimeout() {
        return cacheInvalidateReadTimeout;
    }

    /**
     * Sets the read timeout applied to trade cache invalidation calls.
     *
     * @param cacheInvalidateReadTimeout read timeout, must be positive
     * @author Caspian
     * @since 0.3.0
     */
    public void setCacheInvalidateReadTimeout(Duration cacheInvalidateReadTimeout) {
        this.cacheInvalidateReadTimeout = cacheInvalidateReadTimeout;
    }
}
