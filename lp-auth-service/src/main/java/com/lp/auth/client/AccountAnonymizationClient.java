package com.lp.auth.client;

import com.lp.auth.config.AccountDeletionProperties;
import com.lp.auth.dto.AccountAnonymizationCommand;
import com.lp.auth.dto.AccountAnonymizationView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks the trade service to anonymise the payment rows of a deleted account.
 *
 * <p>Called inside the transaction that anonymises the account itself. A failure is surfaced as
 * {@code 50401} so the caller rolls back and the request stays in the cooling-off table for the
 * next scan; a retry is harmless because the trade statement skips rows already anonymised. The
 * response code is checked explicitly because the envelope carries errors with HTTP {@code 200}.
 *
 * @author Caspian
 * @since 0.3.0
 */
@Component
public class AccountAnonymizationClient {

    private static final Logger LOG = LoggerFactory.getLogger(AccountAnonymizationClient.class);

    private static final ParameterizedTypeReference<Result<AccountAnonymizationView>> VIEW_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from the account cancellation settings.
     *
     * @param properties trade-service address and timeout
     * @param internalToken shared secret required by the trade-service internal API
     * @author Caspian
     * @since 0.3.0
     */
    @Autowired
    public AccountAnonymizationClient(
            AccountDeletionProperties properties,
            @Value("${lp.internal.token}") String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(properties.getTradeReadTimeout());
        this.restClient =
                RestClient.builder()
                        .baseUrl(properties.getTradeBaseUrl())
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Rewrites the payer identifiers of one account on the trade side.
     *
     * @param userId account owner
     * @param salt secret salt mixed into the digest
     * @throws BizException {@code 50401} when the trade service is unreachable or answers unusably
     * @author Caspian
     * @since 0.3.0
     */
    public void anonymize(long userId, String salt) {
        try {
            Result<AccountAnonymizationView> result =
                    restClient
                            .post()
                            .uri("/internal/v1/account-deletion/anonymize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new AccountAnonymizationCommand(userId, salt))
                            .retrieve()
                            .body(VIEW_TYPE);
            if (result == null || result.getCode() != Result.SUCCESS_CODE) {
                LOG.warn("trade anonymisation returned no usable body, userId={}", userId);
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
        } catch (RestClientException exception) {
            LOG.warn("trade anonymisation failed, userId={}", userId, exception);
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }
}
