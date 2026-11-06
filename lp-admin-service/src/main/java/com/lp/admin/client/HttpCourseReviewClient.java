package com.lp.admin.client;

import com.lp.admin.dto.AdminPendingCoursePageView;
import com.lp.admin.dto.AdminReviewResultView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link CourseReviewClient}.
 *
 * <p>Every request carries {@code X-Internal-Token}; a blank token fails startup instead of
 * silently degrading. Transport failures are reported as {@code 50201}, and a failed envelope is
 * re-raised with the code returned by course-service so the console sees the same error.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class HttpCourseReviewClient implements CourseReviewClient {

    private static final ParameterizedTypeReference<Result<AdminPendingCoursePageView>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Result<AdminReviewResultView>> REVIEW_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client and validates the shared internal token.
     *
     * @param baseUrl course-service base URL, for example {@code http://127.0.0.1:8102}
     * @param readTimeoutMs read timeout in milliseconds
     * @param internalToken shared secret required by the course-service internal API
     * @throws IllegalStateException when the token is blank
     * @author Caspian
     * @since 0.1.0
     */
    public HttpCourseReviewClient(
            @Value("${lp.admin.course-base-url}") String baseUrl,
            @Value("${lp.admin.course-read-timeout-ms:3000}") long readTimeoutMs,
            @Value("${lp.internal.token}") String internalToken) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "lp.internal.token is required; set LP_INTERNAL_TOKEN before starting");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(java.time.Duration.ofMillis(readTimeoutMs));
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Loads one page of the review queue.
     *
     * @param pageNum one based page number
     * @param pageSize page size
     * @return pending courses, oldest submission first
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public AdminPendingCoursePageView pending(int pageNum, int pageSize) {
        try {
            Result<AdminPendingCoursePageView> result =
                    restClient
                            .get()
                            .uri(
                                    builder ->
                                            builder.path("/internal/v1/courses/pending")
                                                    .queryParam("pageNum", pageNum)
                                                    .queryParam("pageSize", pageSize)
                                                    .build())
                            .retrieve()
                            .body(PAGE_TYPE);
            return unwrap(result);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    /**
     * Applies a review decision.
     *
     * @param courseId course primary key
     * @param approved {@code true} to publish, {@code false} to send back to draft
     * @param priceFen price in fen set on approval, {@code null} keeps the current price
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operator user id
     * @return course state after the decision
     * @throws BizException when the upstream call fails or the course is not pending
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public AdminReviewResultView review(
            long courseId, boolean approved, Long priceFen, String remark, long reviewerId) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("approved", approved);
        body.put("reviewerId", reviewerId);
        if (priceFen != null) {
            body.put("priceFen", priceFen);
        }
        if (remark != null) {
            body.put("remark", remark);
        }
        try {
            Result<AdminReviewResultView> result =
                    restClient
                            .post()
                            .uri("/internal/v1/courses/{courseId}/review", courseId)
                            .body(body)
                            .retrieve()
                            .body(REVIEW_TYPE);
            return unwrap(result);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
        if (result.getCode() != Result.SUCCESS_CODE) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
