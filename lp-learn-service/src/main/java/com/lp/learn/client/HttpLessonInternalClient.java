package com.lp.learn.client;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import com.lp.learn.config.LearnProperties;
import com.lp.learn.dto.LessonPlayInfo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP implementation of {@link LessonInternalClient}.
 *
 * <p>A transport failure becomes {@code 50201}; a {@code 40401} returned by course-service is
 * re-raised as not found so the play-token endpoint keeps the documented error code.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class HttpLessonInternalClient implements LessonInternalClient {

    private static final ParameterizedTypeReference<Result<LessonPlayInfo>> RESULT_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    /**
     * Creates the client from learn configuration.
     *
     * @param learnProperties course-service address and read timeout
     * @author Caspian
     * @since 0.1.0
     */
    public HttpLessonInternalClient(LearnProperties learnProperties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(learnProperties.getCourseReadTimeout());
        this.restClient =
                RestClient.builder()
                        .baseUrl(learnProperties.getCourseBaseUrl())
                        .requestFactory(factory)
                        .build();
    }

    /**
     * Loads one playable lesson.
     *
     * @param lessonId lesson primary key
     * @return lesson media metadata
     * @throws BizException when the lesson is missing or the upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public LessonPlayInfo lesson(long lessonId) {
        try {
            Result<LessonPlayInfo> result =
                    restClient
                            .get()
                            .uri("/internal/v1/lessons/{lessonId}", lessonId)
                            .retrieve()
                            .body(RESULT_TYPE);
            if (result == null) {
                throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
            }
            if (result.getCode() != Result.SUCCESS_CODE) {
                throw new BizException(result.getCode(), result.getMessage());
            }
            if (result.getData() == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "lesson not found");
            }
            return result.getData();
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }
}
