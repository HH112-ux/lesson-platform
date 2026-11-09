package com.lp.admin.client;

import com.lp.admin.dto.AdminTeacherPageView;
import com.lp.admin.dto.AdminTeacherReviewResultView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link TeacherAuditClient}.
 *
 * <p>Every request carries {@code X-Internal-Token}; a blank token fails startup. A failed envelope
 * is re-raised with the upstream code so the console sees the same error as course-service.
 *
 * @author Caspian
 * @since 0.5.0
 */
@Component
public class HttpTeacherAuditClient implements TeacherAuditClient {

    private static final ParameterizedTypeReference<Result<AdminTeacherPageView>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Result<AdminTeacherReviewResultView>>
            REVIEW_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client and validates the shared internal token.
     *
     * @param baseUrl course-service base URL
     * @param readTimeoutMs read timeout in milliseconds
     * @param internalToken shared secret required by the course-service internal API
     * @throws IllegalStateException when the token is blank
     * @author Caspian
     * @since 0.5.0
     */
    public HttpTeacherAuditClient(
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
     * Loads one page of teacher applications.
     *
     * @param auditStatus optional audit status filter
     * @param keyword optional match on the display name
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of applications
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public AdminTeacherPageView page(
            Integer auditStatus, String keyword, int pageNum, int pageSize) {
        try {
            Result<AdminTeacherPageView> result =
                    restClient
                            .get()
                            .uri(
                                    builder -> {
                                        builder.path("/internal/v1/teachers")
                                                .queryParam("pageNum", pageNum)
                                                .queryParam("pageSize", pageSize)
                                                .queryParamIfPresent(
                                                        "auditStatus",
                                                        Optional.ofNullable(auditStatus))
                                                .queryParamIfPresent(
                                                        "keyword", Optional.ofNullable(keyword));
                                        return builder.build();
                                    })
                            .retrieve()
                            .body(PAGE_TYPE);
            return unwrap(result);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    /**
     * Applies an approval or rejection.
     *
     * @param teacherId profile primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param remark rejection remark, required when rejecting
     * @param reviewerId operator user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @return application state after the decision
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.5.0
     */
    @Override
    public AdminTeacherReviewResultView review(
            long teacherId, boolean approved, String remark, long reviewerId, int operatorRole) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approved", approved);
        body.put("reviewerId", reviewerId);
        body.put("operatorRole", operatorRole);
        if (remark != null) {
            body.put("remark", remark);
        }
        try {
            Result<AdminTeacherReviewResultView> result =
                    restClient
                            .post()
                            .uri("/internal/v1/teachers/{teacherId}/review", teacherId)
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
