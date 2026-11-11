package com.lp.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.lp.common.internal.InternalHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads whether a course already has a paid order from the trade-service internal API.
 *
 * <p>An unreachable trade service yields {@code null} rather than a guess, so the caller can fail
 * closed before a destructive change.
 *
 * @author Caspian
 * @since 0.15.0
 */
@Component
public class TradeOrderClient {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderClient.class);

    private final RestClient restClient;

    /**
     * Creates the client from course configuration.
     *
     * @param tradeBaseUrl trade-service base URL
     * @param internalToken shared secret required by the internal API
     * @param readTimeoutMillis read timeout in milliseconds
     */
    public TradeOrderClient(
            @Value("${lp.course.trade-base-url:http://127.0.0.1:8103}") String tradeBaseUrl,
            @Value("${lp.internal.token}") String internalToken,
            @Value("${lp.course.trade-read-timeout-ms:2000}") int readTimeoutMillis) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(readTimeoutMillis);
        this.restClient =
                RestClient.builder()
                        .baseUrl(tradeBaseUrl)
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Tells whether the course already has at least one paid order.
     *
     * @param courseId course id
     * @return {@code true} sold, {@code false} never sold, {@code null} when trade cannot answer
     * @author Caspian
     * @since 0.15.0
     */
    public Boolean paidOrderExists(long courseId) {
        try {
            JsonNode body =
                    restClient
                            .get()
                            .uri("/internal/v1/orders/paid-exists?courseId={id}", courseId)
                            .retrieve()
                            .body(JsonNode.class);
            if (body == null || !body.path("data").isBoolean()) {
                log.warn("paid order lookup returned no usable data for course {}", courseId);
                return null;
            }
            return body.path("data").asBoolean();
        } catch (RestClientException exception) {
            log.warn(
                    "paid order lookup failed for course {}: {}", courseId, exception.getMessage());
            return null;
        }
    }
}
