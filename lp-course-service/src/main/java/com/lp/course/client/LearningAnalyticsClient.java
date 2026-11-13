package com.lp.course.client;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads learning aggregates from the learn-service internal API.
 *
 * <p>Unlike the catalog owned-flag lookup, a failure here must not degrade silently: the dashboard
 * would otherwise show zeros that look like real numbers. Every failure is converted into {@code
 * 50401} so the page reports an upstream problem instead of pretending there is no learning data.
 *
 * @author Caspian
 * @since 0.16.0
 */
@Component
public class LearningAnalyticsClient {

    private static final Logger log = LoggerFactory.getLogger(LearningAnalyticsClient.class);

    private static final ParameterizedTypeReference<Result<LearningMetricsSnapshot>> METRICS_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from course configuration.
     *
     * @param learnBaseUrl learn-service base URL
     * @param internalToken shared secret required by the learn-service internal API
     * @param readTimeoutMillis read timeout in milliseconds
     * @author Caspian
     * @since 0.16.0
     */
    public LearningAnalyticsClient(
            @Value("${lp.course.learn-base-url:http://127.0.0.1:8104}") String learnBaseUrl,
            @Value("${lp.internal.token}") String internalToken,
            @Value("${lp.course.learn-read-timeout-ms:2000}") int readTimeoutMillis) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(readTimeoutMillis);
        this.restClient =
                RestClient.builder()
                        .baseUrl(learnBaseUrl)
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Returns the raw learning aggregates of the given courses.
     *
     * @param courseIds owned course ids, must not be empty
     * @param days natural day window
     * @return aggregates returned by the learn-service
     * @throws BizException with {@code 50401} when the dependency is unreachable or answers
     *     unusably
     * @author Caspian
     * @since 0.16.0
     */
    public LearningMetricsSnapshot teacherMetrics(List<Long> courseIds, int days) {
        String joined = String.join(",", courseIds.stream().map(String::valueOf).toList());
        try {
            Result<LearningMetricsSnapshot> body =
                    restClient
                            .get()
                            .uri(
                                    builder ->
                                            builder.path("/internal/v1/learning/teacher-analytics")
                                                    .queryParam("courseIds", joined)
                                                    .queryParam("days", days)
                                                    .build())
                            .retrieve()
                            .body(METRICS_TYPE);
            if (body == null || body.getCode() != Result.SUCCESS_CODE || body.getData() == null) {
                throw new BizException(
                        ErrorCode.DEPENDENCY_UNAVAILABLE, "learning analytics is unavailable");
            }
            return body.getData();
        } catch (RestClientException exception) {
            log.warn("learning analytics lookup failed: {}", exception.getMessage());
            throw new BizException(
                    ErrorCode.DEPENDENCY_UNAVAILABLE, "learning analytics is unavailable");
        }
    }
}
