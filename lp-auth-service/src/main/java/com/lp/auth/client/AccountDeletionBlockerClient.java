package com.lp.auth.client;

import com.lp.auth.config.AccountDeletionProperties;
import com.lp.auth.dto.AccountDeletionBlockerView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the trade-side blockers of an account through the trade-service internal API.
 *
 * <p>Fails closed: an unreachable, slow or unusable answer is surfaced as {@code 50401} so the
 * caller refuses the cancellation instead of accepting one that hides live orders. The trade
 * service owns the blocker definition, so this class never counts rows itself.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Component
public class AccountDeletionBlockerClient {

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionBlockerClient.class);

    private static final ParameterizedTypeReference<Result<AccountDeletionBlockerView>>
            BLOCKER_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from the account cancellation settings.
     *
     * @param properties trade-service address and timeout
     * @param internalToken shared secret required by the trade-service internal API
     * @author Caspian
     * @since 0.2.0
     */
    @Autowired
    public AccountDeletionBlockerClient(
            AccountDeletionProperties properties,
            @Value("${lp.internal.token}") String internalToken) {
        this(restClientOf(properties, internalToken));
    }

    /**
     * Creates the client over a prepared {@link RestClient}; used by tests to stub the transport.
     *
     * @param restClient client already bound to the trade-service base URL
     * @author Caspian
     * @since 0.2.0
     */
    AccountDeletionBlockerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Fetches the blocker counts of a user.
     *
     * @param userId account owner
     * @return blocker counts plus the aggregated decision from the trade service
     * @throws BizException {@code 50401} when the trade service is unreachable or answers unusably
     * @author Caspian
     * @since 0.2.0
     */
    public AccountDeletionBlockerView blockers(long userId) {
        try {
            Result<AccountDeletionBlockerView> result =
                    restClient
                            .get()
                            .uri(
                                    builder ->
                                            builder.path("/internal/v1/account-deletion/blockers")
                                                    .queryParam("userId", userId)
                                                    .build())
                            .retrieve()
                            .body(BLOCKER_TYPE);
            if (result == null
                    || result.getCode() != Result.SUCCESS_CODE
                    || result.getData() == null) {
                LOG.warn(
                        "account deletion blocker probe returned no usable body, userId={}",
                        userId);
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            return result.getData();
        } catch (RestClientException exception) {
            LOG.warn("account deletion blocker probe failed, userId={}", userId, exception);
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    /**
     * Builds the production client bound to the configured trade-service address.
     *
     * @param properties trade-service address and timeout
     * @param internalToken shared secret required by the trade-service internal API
     * @return client with the internal token and a short read timeout
     * @author Caspian
     * @since 0.2.0
     */
    private static RestClient restClientOf(
            AccountDeletionProperties properties, String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.getTradeReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getTradeBaseUrl())
                .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                .requestFactory(factory)
                .build();
    }
}
