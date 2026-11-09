package com.lp.trade.client;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.dto.CourseProgressSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link LearnProgressClient}.
 *
 * <p>The refund admission rule fails closed: any transport error, failed envelope or missing body
 * becomes {@code 50201}, so a learn-service outage can never be read as zero progress.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class HttpLearnProgressClient implements LearnProgressClient {

    private static final ParameterizedTypeReference<Result<CourseProgressSummary>> TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from trade configuration.
     *
     * @param tradeProperties learn-service address and read timeout
     * @param internalToken shared secret required by the learn-service internal API
     * @author Caspian
     * @since 0.1.0
     */
    public HttpLearnProgressClient(
            TradeProperties tradeProperties, @Value("${lp.internal.token}") String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(tradeProperties.getLearnReadTimeout());
        this.restClient =
                RestClient.builder()
                        .baseUrl(tradeProperties.getLearnBaseUrl())
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Loads one course progress summary from the learn service.
     *
     * @param userId buyer id
     * @param courseId course id
     * @return progress summary
     * @throws BizException with {@code 50201} whenever learn-service cannot answer
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseProgressSummary progress(long userId, long courseId) {
        try {
            Result<CourseProgressSummary> result =
                    restClient
                            .get()
                            .uri(
                                    builder ->
                                            builder.path("/internal/v1/learning/progress-summary")
                                                    .queryParam("userId", userId)
                                                    .queryParam("courseId", courseId)
                                                    .build())
                            .retrieve()
                            .body(TYPE);
            if (result == null
                    || result.getCode() != Result.SUCCESS_CODE
                    || result.getData() == null) {
                throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
            }
            return result.getData();
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }
}
