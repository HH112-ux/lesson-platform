package com.lp.trade.client;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.dto.AccountDeletionStateView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the cancellation cooling-off state of a buyer from the auth-service internal API.
 *
 * <p>The read timeout is 200 ms because the call sits on the checkout path: a slow auth-service
 * must surface quickly and let the caller refuse the purchase, not stall the buyer. Trade never
 * reads auth tables, so the decision stays owned by auth-service.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Component
public class AccountDeletionStateClient {

    private static final ParameterizedTypeReference<Result<AccountDeletionStateView>> STATE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from trade configuration.
     *
     * @param tradeProperties auth-service address and state read timeout
     * @param internalToken shared secret required by the auth-service internal API
     * @author Caspian
     * @since 0.3.0
     */
    @Autowired
    public AccountDeletionStateClient(
            TradeProperties tradeProperties, @Value("${lp.internal.token}") String internalToken) {
        this(restClientOf(tradeProperties, internalToken));
    }

    /**
     * Creates the client over a prepared {@link RestClient}; used by tests to stub the transport.
     *
     * @param restClient client already bound to the auth-service base URL
     * @author Caspian
     * @since 0.3.0
     */
    AccountDeletionStateClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Fetches the cooling-off state of a buyer.
     *
     * @param userId account owner
     * @return cooling-off decision plus the deadline when one is active
     * @throws BizException {@code 50401} when auth-service is unreachable, slow or answers unusably
     * @author Caspian
     * @since 0.3.0
     */
    public AccountDeletionStateView state(long userId) {
        try {
            Result<AccountDeletionStateView> result =
                    restClient
                            .get()
                            .uri(
                                    builder ->
                                            builder.path("/internal/v1/account-deletion/state")
                                                    .queryParam("userId", userId)
                                                    .build())
                            .retrieve()
                            .body(STATE_TYPE);
            if (result == null
                    || result.getCode() != Result.SUCCESS_CODE
                    || result.getData() == null) {
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            return result.getData();
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    /**
     * Builds the production client bound to the configured auth-service address.
     *
     * @param tradeProperties auth-service address and state read timeout
     * @param internalToken shared secret required by the auth-service internal API
     * @return client with the internal token and the checkout path timeout
     * @author Caspian
     * @since 0.3.0
     */
    private static RestClient restClientOf(TradeProperties tradeProperties, String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(tradeProperties.getAuthStateReadTimeout());
        return RestClient.builder()
                .baseUrl(tradeProperties.getAuthBaseUrl())
                .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                .requestFactory(factory)
                .build();
    }
}
