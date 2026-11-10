package com.lp.trade.client;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.dto.AuditLogPayload;
import com.lp.trade.entity.AuditOutbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link CourseAuditClient}.
 *
 * <p>Posts to {@code /internal/v1/audit-logs} with the shared internal token. Transport failures
 * and failed envelopes are raised as {@code 50201} so the relay applies its retry policy.
 *
 * @author Caspian
 * @since 0.6.0
 */
@Component
public class HttpCourseAuditClient implements CourseAuditClient {

    private static final ParameterizedTypeReference<Result<Void>> VOID_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from trade configuration.
     *
     * @param tradeProperties course-service address and read timeout
     * @param internalToken shared secret required by the course-service internal API
     * @author Caspian
     * @since 0.6.0
     */
    public HttpCourseAuditClient(
            TradeProperties tradeProperties, @Value("${lp.internal.token}") String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(tradeProperties.getCourseReadTimeout());
        this.restClient =
                RestClient.builder()
                        .baseUrl(tradeProperties.getCourseBaseUrl())
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Appends one audit intent to course-service.
     *
     * @param outbox intent to deliver
     * @throws BizException with {@code 50201} when the upstream call fails
     * @author Caspian
     * @since 0.6.0
     */
    @Override
    public void append(AuditOutbox outbox) {
        AuditLogPayload payload =
                new AuditLogPayload(
                        outbox.getOperatorId(),
                        outbox.getOperatorRole(),
                        outbox.getActionType(),
                        outbox.getObjectType(),
                        outbox.getObjectId(),
                        outbox.getFromStatus(),
                        outbox.getToStatus(),
                        outbox.getRemark(),
                        outbox.getTraceId(),
                        outbox.getDedupeKey(),
                        outbox.getOccurredAt());
        try {
            Result<Void> result =
                    restClient
                            .post()
                            .uri("/internal/v1/audit-logs")
                            .body(payload)
                            .retrieve()
                            .body(VOID_TYPE);
            if (result == null || result.getCode() != Result.SUCCESS_CODE) {
                throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
            }
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }
}
