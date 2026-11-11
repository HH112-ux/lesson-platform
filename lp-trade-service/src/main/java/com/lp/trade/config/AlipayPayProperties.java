package com.lp.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Alipay openapi channel.
 *
 * <p>Credentials are never copied into configuration or code: {@code credentialsFile} points at the
 * verified credential file and the loader reads it directly at startup. The gateway URL defaults to
 * the sandbox and must be replaced by the production gateway before a real release.
 *
 * @author Caspian
 * @since 0.4.0
 */
@ConfigurationProperties(prefix = "lp.pay.alipay")
public class AlipayPayProperties {

    private String credentialsFile = "";

    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    private String notifyUrl = "";

    /**
     * Returns the absolute path of the credential file read at startup.
     *
     * @return absolute path of the verified credential file, empty when unset
     * @author Caspian
     * @since 0.4.0
     */
    public String getCredentialsFile() {
        return credentialsFile;
    }

    /**
     * Sets the absolute path of the credential file read at startup.
     *
     * @param credentialsFile absolute path of the verified credential file
     * @author Caspian
     * @since 0.4.0
     */
    public void setCredentialsFile(String credentialsFile) {
        this.credentialsFile = credentialsFile;
    }

    /**
     * Returns the openapi gateway URL.
     *
     * @return gateway URL, sandbox by default
     * @author Caspian
     * @since 0.4.0
     */
    public String getGatewayUrl() {
        return gatewayUrl;
    }

    /**
     * Sets the openapi gateway URL.
     *
     * @param gatewayUrl gateway URL supplied by configuration
     * @author Caspian
     * @since 0.4.0
     */
    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    /**
     * Returns the public callback URL sent to the channel with each payment request.
     *
     * @return absolute notify URL, empty when the deployment has no public address
     * @author Caspian
     * @since 0.4.0
     */
    public String getNotifyUrl() {
        return notifyUrl;
    }

    /**
     * Sets the public callback URL sent to the channel with each payment request.
     *
     * @param notifyUrl absolute notify URL reachable from the internet
     * @author Caspian
     * @since 0.4.0
     */
    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }
}
