package com.lp.learn.client;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.learn.config.LearnProperties;
import com.lp.learn.dto.CourseInternalView;
import java.util.List;
import java.util.stream.Collectors;
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

    private static final ParameterizedTypeReference<Result<List<CourseInternalView>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from learn configuration.
     *
     * @param learnProperties course-service address and read timeout
     * @author Caspian
     * @since 0.1.0
     */
    public HttpCourseInternalClient(LearnProperties learnProperties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(learnProperties.getCourseReadTimeout());
        this.restClient =
                RestClient.builder()
                        .baseUrl(learnProperties.getCourseBaseUrl())
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Loads a batch of courses from the internal API.
     *
     * @param courseIds course ids, may be empty
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
            Result<List<CourseInternalView>> result =
                    restClient
                            .get()
                            .uri(
                                    builder ->
                                            builder.path("/internal/v1/courses")
                                                    .queryParam("ids", ids)
                                                    .build())
                            .retrieve()
                            .body(LIST_TYPE);
            if (result == null) {
                throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
            }
            if (result.getCode() != Result.SUCCESS_CODE) {
                throw new BizException(result.getCode(), result.getMessage());
            }
            List<CourseInternalView> data = result.getData();
            if (data == null) {
                return List.of();
            }
            return data.stream()
                    .map(
                            view ->
                                    new CourseSnapshot(
                                            view.getCourseId(),
                                            view.getTitle(),
                                            view.getCoverUrl()))
                    .toList();
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }
}
