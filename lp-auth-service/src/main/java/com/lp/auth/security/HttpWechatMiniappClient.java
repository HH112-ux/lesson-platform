package com.lp.auth.security;

import com.lp.auth.config.MiniappProperties;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Real {@code jscode2session} client.
 *
 * <p>It is the default implementation and is replaced only when {@code mock-enabled} is true. The
 * AppID and AppSecret are read from configuration at call time, so a deployment with the feature
 * disabled still starts without secrets. Any transport failure, non-zero {@code errcode} or missing
 * openid is reported as {@code 66001}; the upstream body is never echoed or logged.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
@ConditionalOnProperty(
        prefix = "lp.auth.miniapp",
        name = "mock-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class HttpWechatMiniappClient implements WechatMiniappClient {

    private static final String SESSION_PATH = "/sns/jscode2session";

    private static final String GRANT_TYPE = "authorization_code";

    private final MiniappProperties properties;

    private final RestClient restClient;

    /**
     * Creates the client with the configured timeouts.
     *
     * @param properties miniapp configuration
     * @author Caspian
     * @since 0.1.0
     */
    public HttpWechatMiniappClient(MiniappProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient =
                RestClient.builder()
                        .requestFactory(factory)
                        .baseUrl(properties.getBaseUrl())
                        .build();
    }

    /**
     * Calls {@code jscode2session} and validates the upstream answer.
     *
     * @param code one-time code from {@code wx.login}
     * @return resolved WeChat identity
     * @throws BizException {@code 66001} when the secret is missing, the call fails or WeChat
     *     rejects the code
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public WechatSession exchange(String code) {
        if (properties.getAppId().isBlank() || properties.getAppSecret().isBlank()) {
            throw new BizException(
                    ErrorCode.MINIAPP_LOGIN_BIZ_ERROR, "miniapp appid or secret is not configured");
        }

        Map<String, Object> body;
        try {
            body =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(SESSION_PATH)
                                                    .queryParam("appid", properties.getAppId())
                                                    .queryParam("secret", properties.getAppSecret())
                                                    .queryParam("js_code", code)
                                                    .queryParam("grant_type", GRANT_TYPE)
                                                    .build())
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException ex) {
            throw new BizException(
                    ErrorCode.MINIAPP_LOGIN_BIZ_ERROR, "wechat jscode2session failed");
        }

        if (body == null) {
            throw new BizException(
                    ErrorCode.MINIAPP_LOGIN_BIZ_ERROR, "wechat returned an empty body");
        }
        Object errcode = body.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            throw new BizException(
                    ErrorCode.MINIAPP_LOGIN_BIZ_ERROR, "wechat rejected the login code");
        }
        String openid = asText(body.get("openid"));
        if (openid == null || openid.isBlank()) {
            throw new BizException(ErrorCode.MINIAPP_LOGIN_BIZ_ERROR, "wechat returned no openid");
        }
        return new WechatSession(
                openid, asText(body.get("unionid")), asText(body.get("session_key")));
    }

    /**
     * Converts a JSON value to text without trusting its type.
     *
     * @param value raw JSON value
     * @return text value, or {@code null} when absent
     * @author Caspian
     * @since 0.1.0
     */
    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
