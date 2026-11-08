package com.lp.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the local mock payment provider.
 *
 * <p>The mock provider is a development-only channel adapter. It implements the same {@code
 * PaymentGateway} and {@code SignatureVerifier} ports as a real channel, so callbacks still pass
 * through signature verification; only the signer lives on the local machine. Enabling it outside
 * dev or local is rejected at startup by {@link com.lp.trade.payment.MockPayProfileGuard}.
 *
 * @author Caspian
 * @since 0.2.0
 */
@ConfigurationProperties(prefix = "lp.pay.mock")
public class MockPayProperties {

    private boolean enabled;

    private String secret;

    private String gatewayBaseUrl = "http://127.0.0.1:8080";

    /**
     * Returns whether the mock provider replaces the unconfigured channel adapter.
     *
     * @return {@code true} when the mock provider is active
     * @author Caspian
     * @since 0.2.0
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the mock provider is active.
     *
     * @param enabled {@code true} to activate the mock provider
     * @author Caspian
     * @since 0.2.0
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the shared secret used to sign local callbacks.
     *
     * @return HMAC secret
     * @author Caspian
     * @since 0.2.0
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the shared secret used to sign local callbacks.
     *
     * @param secret HMAC secret
     * @author Caspian
     * @since 0.2.0
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * Returns the gateway base URL used to build the local payment link.
     *
     * @return scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.2.0
     */
    public String getGatewayBaseUrl() {
        return gatewayBaseUrl;
    }

    /**
     * Sets the gateway base URL used to build the local payment link.
     *
     * @param gatewayBaseUrl scheme, host and port without a trailing slash
     * @author Caspian
     * @since 0.2.0
     */
    public void setGatewayBaseUrl(String gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl;
    }
}
