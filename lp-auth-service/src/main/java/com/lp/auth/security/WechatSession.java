package com.lp.auth.security;

/**
 * Result of exchanging a miniapp login code with WeChat.
 *
 * <p>The session key is stored for future server-side decryption and must never reach a log line or
 * a API response.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class WechatSession {

    private final String openid;

    private final String unionid;

    private final String sessionKey;

    /**
     * Creates the session result.
     *
     * @param openid WeChat openid
     * @param unionid WeChat unionid, may be {@code null}
     * @param sessionKey WeChat session key, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public WechatSession(String openid, String unionid, String sessionKey) {
        this.openid = openid;
        this.unionid = unionid;
        this.sessionKey = sessionKey;
    }

    /**
     * Returns the WeChat openid.
     *
     * @return openid
     * @author Caspian
     * @since 0.1.0
     */
    public String getOpenid() {
        return openid;
    }

    /**
     * Returns the WeChat unionid.
     *
     * @return unionid, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getUnionid() {
        return unionid;
    }

    /**
     * Returns the WeChat session key.
     *
     * @return session key, never logged
     * @author Caspian
     * @since 0.1.0
     */
    public String getSessionKey() {
        return sessionKey;
    }
}
