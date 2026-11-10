package com.lp.admin.client;

import com.lp.admin.dto.AdminAuditLogPageView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link AdminAuditClient}.
 *
 * <p>Calls the token guarded course-service internal API. Transport failures become {@code 50201};
 * a business rejection keeps the upstream code so the console can show the exact reason.
 *
 * @author Caspian
 * @since 0.6.0
 */
@Component
public class HttpAdminAuditClient implements AdminAuditClient {

    private static final ParameterizedTypeReference<Result<AdminAuditLogPageView>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from admin configuration.
     *
     * @param baseUrl course-service base URL
     * @param readTimeoutMs read timeout in milliseconds
     * @param internalToken shared secret required by the internal API
     * @author Caspian
     * @since 0.6.0
     */
    public HttpAdminAuditClient(
            @Value("${lp.admin.course-base-url}") String baseUrl,
            @Value("${lp.admin.course-read-timeout-ms:3000}") long readTimeoutMs,
            @Value("${lp.internal.token}") String internalToken) {
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
     * Reads one page of audit entries from course-service.
     *
     * @param objectType object type code
     * @param objectId object primary key
     * @param operatorId operator user id
     * @param from inclusive lower bound
     * @param to exclusive upper bound
     * @param pageNum one based page number
     * @param pageSize page size
     * @return one page of audit entries
     * @throws BizException when the upstream call fails or rejects the query
     * @author Caspian
     * @since 0.6.0
     */
    @Override
    public AdminAuditLogPageView page(
            Integer objectType,
            Long objectId,
            Long operatorId,
            LocalDateTime from,
            LocalDateTime to,
            int pageNum,
            int pageSize) {
        try {
            Result<AdminAuditLogPageView> result =
                    restClient
                            .get()
                            .uri(
                                    builder -> {
                                        builder.path("/internal/v1/audit-logs")
                                                .queryParam("pageNum", pageNum)
                                                .queryParam("pageSize", pageSize);
                                        if (objectType != null) {
                                            builder.queryParam("objectType", objectType);
                                        }
                                        if (objectId != null) {
                                            builder.queryParam("objectId", objectId);
                                        }
                                        if (operatorId != null) {
                                            builder.queryParam("operatorId", operatorId);
                                        }
                                        if (from != null) {
                                            builder.queryParam("from", from.toString());
                                        }
                                        if (to != null) {
                                            builder.queryParam("to", to.toString());
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
