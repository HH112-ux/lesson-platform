package com.lp.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.lp.common.identity.IdentityHeaders;
import com.lp.common.internal.InternalHeaders;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the current user's owned course ids from the learn-service internal API.
 *
 * <p>The owned flag is a convenience for the catalog. A learn-service outage must not take the
 * storefront down, so failures degrade to "nothing owned" and are logged without any user payload.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class LearnEntitlementClient {

    private static final Logger log = LoggerFactory.getLogger(LearnEntitlementClient.class);

    private final RestClient restClient;

    /**
     * Creates the client from course configuration.
     *
     * @param learnBaseUrl learn-service base URL
     * @param internalToken shared secret required by the learn-service internal API
     * @param readTimeoutMillis read timeout in milliseconds
     * @author Caspian
     * @since 0.1.0
     */
    public LearnEntitlementClient(
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
     * Returns the subset of course ids the user owns.
     *
     * @param userId user id from the gateway
     * @param courseIds candidate course ids
     * @return owned ids; empty on any upstream failure
     * @author Caspian
     * @since 0.1.0
     */
    public Set<Long> ownedCourseIds(long userId, List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Set.of();
        }
        String joined = String.join(",", courseIds.stream().map(String::valueOf).toList());
        try {
            JsonNode body =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/internal/v1/entitlements/owned")
                                                    .queryParam("courseIds", joined)
                                                    .build())
                            .header(IdentityHeaders.USER_ID, Long.toString(userId))
                            .retrieve()
                            .body(JsonNode.class);
            if (body == null) {
                return Set.of();
            }
            Set<Long> owned = new HashSet<>();
            for (JsonNode id : body.path("data").path("courseIds")) {
                owned.add(id.asLong());
            }
            return owned;
        } catch (RestClientException exception) {
            log.warn(
                    "owned course lookup failed, degrading to anonymous: {}",
                    exception.getMessage());
            return Set.of();
        }
    }
}
