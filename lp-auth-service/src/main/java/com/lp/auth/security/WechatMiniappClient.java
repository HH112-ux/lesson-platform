package com.lp.auth.security;

/**
 * Exchanges a miniapp login code for a WeChat session.
 *
 * <p>Implementations must never log the code, the AppSecret or the session key.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface WechatMiniappClient {

    /**
     * Calls {@code jscode2session} and returns the resolved identity.
     *
     * @param code one-time code from {@code wx.login}
     * @return openid, unionid and session key
     * @throws com.lp.common.exception.BizException {@code 66001} when WeChat rejects the code or
     *     the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    WechatSession exchange(String code);
}
