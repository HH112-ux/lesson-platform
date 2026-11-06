package com.lp.admin.client;

import com.lp.admin.dto.AdminOrderPageView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link AdminOrderClient}.
 *
 * <p>Every call carries {@code X-Internal-Token}; a blank token fails startup instead of silently
 * degrading. Transport failures collapse into {@code 50201}, and a failed envelope is re-raised
 * with the code returned by trade-service.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Component
public class HttpAdminOrderClient implements AdminOrderClient {

    private static final ParameterizedTypeReference<Result<AdminOrderPageView>> PAGE_TYPE =
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
     * @since 0.2.0
     */
    public HttpAdminOrderClient(
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
     * Lists one page of orders from trade-service.
     *
     * @param status optional order status
     * @param channel optional payment channel
     * @param orderNo optional order number fragment
     * @param userId optional buyer id
     * @param dateFrom optional inclusive creation date
     * @param dateTo optional inclusive creation date upper bound
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of orders
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    public AdminOrderPageView page(
            Integer status,
            Integer channel,
            String orderNo,
            Long userId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int pageNum,
            int pageSize) {
        try {
            Result<AdminOrderPageView> result =
                    restClient
                            .get()
                            .uri(
                                    builder -> {
                                        builder.path("/internal/v1/orders")
                                                .queryParam("pageNum", pageNum)
                                                .queryParam("pageSize", pageSize);
                                        if (status != null) {
                                            builder.queryParam("status", status);
                                        }
                                        if (channel != null) {
                                            builder.queryParam("channel", channel);
                                        }
                                        if (orderNo != null && !orderNo.isBlank()) {
                                            builder.queryParam("orderNo", orderNo);
                                        }
                                        if (userId != null) {
                                            builder.queryParam("userId", userId);
                                        }
                                        if (dateFrom != null) {
                                            builder.queryParam("dateFrom", dateFrom.toString());
                                        }
                                        if (dateTo != null) {
                                            builder.queryParam("dateTo", dateTo.toString());
                                        }
                                        return builder.build();
                                    })
                            .retrieve()
                            .body(PAGE_TYPE);
            if (result == null) {
                throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
            }
            if (result.getCode() != Result.SUCCESS_CODE) {
                throw new BizException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }
}
