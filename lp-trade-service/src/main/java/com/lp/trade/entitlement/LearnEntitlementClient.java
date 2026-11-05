package com.lp.trade.entitlement;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.config.TradeProperties;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the learn-service internal entitlement API.
 *
 * <p>Only the relay uses this client, always outside a business transaction. Failures are surfaced
 * to the relay so it can schedule a retry.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class LearnEntitlementClient {

    private final RestClient restClient;

    /**
     * Creates the client from trade configuration.
     *
     * @param tradeProperties learn-service address
     * @author Caspian
     * @since 0.1.0
     */
    public LearnEntitlementClient(TradeProperties tradeProperties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(tradeProperties.getLearnReadTimeout());
        this.restClient =
                RestClient.builder()
                        .baseUrl(tradeProperties.getLearnBaseUrl())
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Delivers one entitlement grant.
     *
     * @param userId buyer user id
     * @param courseId course id
     * @param orderId order id
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    public void grant(long userId, long courseId, long orderId) {
        Map<String, Object> body =
                Map.of("userId", userId, "courseId", courseId, "orderId", orderId, "source", 1);
        try {
            restClient
                    .post()
                    .uri("/internal/v1/entitlements")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }
}
