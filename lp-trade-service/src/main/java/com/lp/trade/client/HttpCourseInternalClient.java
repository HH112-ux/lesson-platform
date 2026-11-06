package com.lp.trade.client;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.internal.InternalHeaders;
import com.lp.common.result.Result;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.dto.CourseSnapshot;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link CourseInternalClient}.
 *
 * <p>Calls the course-service internal API with a fixed read timeout. A transport failure is
 * degraded to {@code 50201}; a failed envelope is re-raised with the code returned by
 * course-service so callers keep the same semantics (for example {@code 40401}).
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class HttpCourseInternalClient implements CourseInternalClient {

    private static final ParameterizedTypeReference<Result<CourseSnapshot>> ONE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Result<List<CourseSnapshot>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from trade configuration.
     *
     * @param tradeProperties course-service address and read timeout
     * @param internalToken shared secret required by the course-service internal API
     * @author Caspian
     * @since 0.1.0
     */
    public HttpCourseInternalClient(
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
     * Loads one course from the internal API.
     *
     * @param courseId course primary key
     * @return course snapshot
     * @throws BizException when the course is missing or the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseSnapshot course(long courseId) {
        try {
            Result<CourseSnapshot> result =
                    restClient
                            .get()
                            .uri("/internal/v1/courses/{courseId}", courseId)
                            .retrieve()
                            .body(ONE_TYPE);
            return unwrap(result);
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    /**
     * Loads a batch of courses from the internal API.
     *
     * @param courseIds course primary keys, may be empty
     * @return existing snapshots, possibly empty
     * @throws BizException when the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<CourseSnapshot> courses(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        String ids = courseIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            Result<List<CourseSnapshot>> result =
                    restClient
                            .get()
                            .uri(
                                    builder ->
                                            builder.path("/internal/v1/courses")
                                                    .queryParam("ids", ids)
                                                    .build())
                            .retrieve()
                            .body(LIST_TYPE);
            List<CourseSnapshot> data = unwrap(result);
            return data == null ? List.of() : data;
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
