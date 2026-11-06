package com.lp.admin.client;

import com.lp.admin.dto.AdminUserPageView;
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
 * HTTP implementation of {@link AdminUserClient}.
 *
 * <p>Every call carries {@code X-Internal-Token}; a blank token fails startup instead of silently
 * degrading. Transport failures collapse into {@code 50201}, and a failed envelope is re-raised
 * with the code returned by auth-service.
 *
 * @author Caspian
 * @since 0.2.0
 */
@Component
public class HttpAdminUserClient implements AdminUserClient {

    private static final ParameterizedTypeReference<Result<AdminUserPageView>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client and validates the shared internal token.
     *
     * @param baseUrl auth-service base URL
     * @param readTimeoutMs read timeout in milliseconds
     * @param internalToken shared secret required by the internal API
     * @throws IllegalStateException when the token is blank
     * @author Caspian
     * @since 0.2.0
     */
    public HttpAdminUserClient(
            @Value("${lp.admin.auth-base-url}") String baseUrl,
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
     * Lists one page of accounts from auth-service.
     *
     * @param keyword optional match on username, nickname or phone
     * @param status optional account status filter
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of accounts
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    public AdminUserPageView page(String keyword, Integer status, int pageNum, int pageSize) {
        try {
            Result<AdminUserPageView> result =
                    restClient
                            .get()
                            .uri(
                                    builder -> {
                                        builder.path("/internal/v1/users")
                                                .queryParam("pageNum", pageNum)
                                                .queryParam("pageSize", pageSize);
                                        if (keyword != null && !keyword.isBlank()) {
                                            builder.queryParam("keyword", keyword);
                                        }
                                        if (status != null) {
                                            builder.queryParam("status", status);
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
