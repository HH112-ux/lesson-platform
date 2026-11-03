package com.lp.auth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic {@code jscode2session} stand-in for local runs and tests.
 *
 * <p>The same code always maps to the same openid, so a first call creates an account and a
 * repeated call exercises the existing-user path. It is active only when {@code mock-enabled} is
 * true and must never be enabled outside local or test environments.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
@ConditionalOnProperty(prefix = "lp.auth.miniapp", name = "mock-enabled", havingValue = "true")
public class MockWechatMiniappClient implements WechatMiniappClient {

    /**
     * Maps a code to a stable openid and session key.
     *
     * @param code one-time code from {@code wx.login}
     * @return fixed identity derived from the code
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public WechatSession exchange(String code) {
        return new WechatSession("mock-openid-" + code, null, "mock-session-" + code);
    }
}
