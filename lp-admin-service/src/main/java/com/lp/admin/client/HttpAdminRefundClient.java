package com.lp.admin.client;

import com.lp.admin.dto.AdminRefundPageView;
import com.lp.admin.dto.AdminRefundView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link AdminRefundClient}.
 *
 * <p>Calls trade-service over the token guarded internal API. Transport failures become {@code
 * 50201}; a business rejection keeps the upstream code so the console can show the reason.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class HttpAdminRefundClient implements AdminRefundClient {

    private static final ParameterizedTypeReference<Result<AdminRefundPageView>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Result<AdminRefundView>> VIEW_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client and validates the shared internal token.
     *
     * @param baseUrl trade-service base URL
     * @param readTimeoutMs read timeout in milliseconds
     * @param internalToken shared secret required by the internal API
     * @throws IllegalStateException when the token is blank
     * @author Caspian
     * @since 0.1.0
     */
    public HttpAdminRefundClient(
            @Value("${lp.admin.trade-base-url}") String baseUrl,
            @Value("${lp.admin.course-read-timeout-ms:3000}") long readTimeoutMs,
            @Value("${lp.internal.token}") String internalToken) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "lp.internal.token is required; set LP_INTERNAL_TOKEN before starting");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Lists one page of refunds from trade-service.
     *
     * @param status optional refund status
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of refunds
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public AdminRefundPageView page(Integer status, int pageNum, int pageSize) {
        try {
            Result<AdminRefundPageView> result =
                    restClient
                            .get()
                            .uri(
                                    builder -> {
                                        builder.path("/internal/v1/refunds")
                                                .queryParam("pageNum", pageNum)
                                                .queryParam("pageSize", pageSize);
                                        if (status != null) {
                                            builder.queryParam("status", status);
                                        }
                                        return builder.build();
                                    })
                            .retrieve()
                            .body(PAGE_TYPE);
            return requireData(result);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    /**
     * Sends one review decision to trade-service.
     *
     * @param refundId refund primary key
     * @param approved {@code true} to approve, {@code false} to reject
     * @param rejectReason rejection reason
     * @param reviewerId operations user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @return refund state after the decision
     * @throws BizException when the upstream call fails or rejects the decision
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public AdminRefundView review(
            long refundId,
            boolean approved,
            String rejectReason,
            long reviewerId,
            int operatorRole) {
        try {
            Result<AdminRefundView> result =
                    restClient
                            .post()
                            .uri("/internal/v1/refunds/{refundId}/review", refundId)
                            .body(new ReviewBody(approved, rejectReason, reviewerId, operatorRole))
                            .retrieve()
                            .body(VIEW_TYPE);
            return requireData(result);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    private static <T> T requireData(Result<T> result) {
        if (result == null) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
        if (result.getCode() != Result.SUCCESS_CODE) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    /**
     * Request body for the internal review endpoint.
     *
     * @param approved whether the request is approved
     * @param rejectReason rejection reason
     * @param reviewerId operations user id
     * @param operatorRole operator role code, 0 operations, 1 administrator
     * @author Caspian
     * @since 0.1.0
     */
    private record ReviewBody(
            Boolean approved, String rejectReason, Long reviewerId, Integer operatorRole) {}
}
