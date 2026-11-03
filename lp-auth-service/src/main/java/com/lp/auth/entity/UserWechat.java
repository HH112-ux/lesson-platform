package com.lp.auth.entity;

/**
 * WeChat miniapp binding row of {@code sys_user_wechat}.
 *
 * <p>The binding maps an {@code openid} to an account. The session key is only used by future
 * server-side decryption; it is never logged or returned to a client.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class UserWechat {

    private Long userId;

    private String openid;

    private String unionid;

    private String sessionKey;

    /**
     * Returns the bound account id.
     *
     * @return {@code sys_user.id}
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the bound account id.
     *
     * @param userId account id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
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
     * Sets the WeChat openid.
     *
     * @param openid openid
     * @author Caspian
     * @since 0.1.0
     */
    public void setOpenid(String openid) {
        this.openid = openid;
    }

    /**
     * Returns the WeChat unionid when the miniapp is bound to an open platform.
     *
     * @return unionid, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getUnionid() {
        return unionid;
    }

    /**
     * Sets the WeChat unionid.
     *
     * @param unionid unionid
     * @author Caspian
     * @since 0.1.0
     */
    public void setUnionid(String unionid) {
        this.unionid = unionid;
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

    /**
     * Sets the WeChat session key.
     *
     * @param sessionKey session key returned by {@code jscode2session}
     * @author Caspian
     * @since 0.1.0
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }
}
