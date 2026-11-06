package com.lp.trade.purchase;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import com.lp.trade.config.TradeProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Issues a browser token pair through the auth-service internal API.
 *
 * <p>Trading a ticket for tokens happens server to server so the trade service never touches JWT
 * private keys. The auth-service validates the shared internal token and the account status.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class AuthSessionClient {

    private static final ParameterizedTypeReference<Result<SessionTokens>> TOKEN_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from trade configuration.
     *
     * @param tradeProperties auth-service address
     * @param internalToken shared secret required by the auth-service internal API
     * @author Caspian
     * @since 0.1.0
     */
    public AuthSessionClient(
            TradeProperties tradeProperties, @Value("${lp.internal.token}") String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient =
                RestClient.builder()
                        .baseUrl(tradeProperties.getAuthBaseUrl())
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Issues a token pair for a user proven by a purchase ticket.
     *
     * @param userId owner user id
     * @return access and refresh tokens
     * @throws BizException {@code 50201} when auth-service is unreachable or rejects the call
     * @author Caspian
     * @since 0.1.0
     */
    public SessionTokens issue(long userId) {
        try {
            Result<SessionTokens> result =
                    restClient
                            .post()
                            .uri("/internal/v1/auth/session")
                            .body(Map.of("userId", userId))
                            .retrieve()
                            .body(TOKEN_TYPE);
            if (result == null || result.getCode() != Result.SUCCESS_CODE) {
                throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
            }
            return result.getData();
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    /**
     * Token pair returned by auth-service.
     *
     * @author Caspian
     * @since 0.1.0
     */
    public static class SessionTokens {

        private String accessToken;

        private String refreshToken;

        private long expiresIn;

        /**
         * Returns the access token.
         *
         * @return signed access token
         * @author Caspian
         * @since 0.1.0
         */
        public String getAccessToken() {
            return accessToken;
        }

        /**
         * Sets the access token.
         *
         * @param accessToken signed access token
         * @author Caspian
         * @since 0.1.0
         */
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        /**
         * Returns the refresh token.
         *
         * @return opaque refresh token
         * @author Caspian
         * @since 0.1.0
         */
        public String getRefreshToken() {
            return refreshToken;
        }

        /**
         * Sets the refresh token.
         *
         * @param refreshToken opaque refresh token
         * @author Caspian
         * @since 0.1.0
         */
        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        /**
         * Returns the access token lifetime in seconds.
         *
         * @return lifetime in seconds
         * @author Caspian
         * @since 0.1.0
         */
        public long getExpiresIn() {
            return expiresIn;
        }

        /**
         * Sets the access token lifetime in seconds.
         *
         * @param expiresIn lifetime in seconds
         * @author Caspian
         * @since 0.1.0
         */
        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }
    }
}
