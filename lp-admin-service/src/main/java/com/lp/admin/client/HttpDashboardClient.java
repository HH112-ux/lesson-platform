package com.lp.admin.client;

import com.lp.admin.dto.AdminCourseStats;
import com.lp.admin.dto.AdminDashboardView;
import com.lp.admin.dto.AdminMetricsOverviewView;
import com.lp.admin.dto.AdminOrderMetrics;
import com.lp.admin.dto.AdminOrderStats;
import com.lp.admin.dto.AdminUserStats;
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
 * HTTP implementation of {@link DashboardClient}.
 *
 * <p>Each upstream service is queried over its internal API with {@code X-Internal-Token}; a blank
 * token fails startup instead of silently degrading. Any transport failure collapses into {@code
 * 50201} because a partially filled dashboard would mislead operations.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class HttpDashboardClient implements DashboardClient {

    private static final ParameterizedTypeReference<Result<AdminCourseStats>> COURSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Result<AdminOrderStats>> ORDER_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Result<AdminUserStats>> USER_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Result<AdminOrderMetrics>> METRICS_TYPE =
            new ParameterizedTypeReference<>() {};

    /** Dashboard window in days; the console currently has no date picker. */
    private static final int OVERVIEW_DAYS = 7;

    private final RestClient courseClient;
    private final RestClient tradeClient;
    private final RestClient authClient;

    /**
     * Creates the client and validates the shared internal token.
     *
     * @param courseBaseUrl course-service base URL
     * @param tradeBaseUrl trade-service base URL
     * @param authBaseUrl auth-service base URL
     * @param readTimeoutMs read timeout in milliseconds
     * @param internalToken shared secret required by every internal API
     * @throws IllegalStateException when the token is blank
     * @author Caspian
     * @since 0.1.0
     */
    public HttpDashboardClient(
            @Value("${lp.admin.course-base-url}") String courseBaseUrl,
            @Value("${lp.admin.trade-base-url}") String tradeBaseUrl,
            @Value("${lp.admin.auth-base-url}") String authBaseUrl,
            @Value("${lp.admin.course-read-timeout-ms:3000}") long readTimeoutMs,
            @Value("${lp.internal.token}") String internalToken) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "lp.internal.token is required; set LP_INTERNAL_TOKEN before starting");
        }
        this.courseClient = build(courseBaseUrl, readTimeoutMs, internalToken);
        this.tradeClient = build(tradeBaseUrl, readTimeoutMs, internalToken);
        this.authClient = build(authBaseUrl, readTimeoutMs, internalToken);
    }

    /**
     * Aggregates course, order and account totals.
     *
     * @return dashboard figures
     * @throws BizException when an upstream call fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public AdminDashboardView load() {
        try {
            AdminCourseStats courses = get(courseClient, "/internal/v1/courses/stats", COURSE_TYPE);
            AdminOrderStats orders = get(tradeClient, "/internal/v1/orders/stats", ORDER_TYPE);
            AdminUserStats users = get(authClient, "/internal/v1/users/stats", USER_TYPE);
            return new AdminDashboardView(
                    courses.total(),
                    courses.pendingReview(),
                    courses.published(),
                    orders.totalOrders(),
                    orders.paidOrders(),
                    orders.paidAmountFen(),
                    users.totalUsers());
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    /**
     * Aggregates the overview metrics of the last seven days.
     *
     * @return revenue, conversion, signups, best sellers and course counters
     * @throws BizException when an upstream call fails
     * @author Caspian
     * @since 0.2.0
     */
    @Override
    public AdminMetricsOverviewView overview() {
        try {
            AdminCourseStats courses = get(courseClient, "/internal/v1/courses/stats", COURSE_TYPE);
            AdminOrderMetrics orders =
                    get(
                            tradeClient,
                            "/internal/v1/orders/metrics?days=" + OVERVIEW_DAYS,
                            METRICS_TYPE);
            AdminUserStats users = get(authClient, "/internal/v1/users/stats", USER_TYPE);
            return new AdminMetricsOverviewView(
                    orders.gmv(),
                    orders.orderCount(),
                    orders.paidRate(),
                    users.newUsers(),
                    orders.courseSalesTop5(),
                    courses.total(),
                    courses.pendingReview(),
                    users.totalUsers());
        } catch (RestClientException exception) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
    }

    private RestClient build(String baseUrl, long readTimeoutMs, String internalToken) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                .requestFactory(factory)
                .build();
    }

    private <T> T get(RestClient client, String path, ParameterizedTypeReference<Result<T>> type) {
        Result<T> result = client.get().uri(path).retrieve().body(type);
        if (result == null) {
            throw new BizException(ErrorCode.CHANNEL_UPSTREAM_ERROR);
        }
        if (result.getCode() != Result.SUCCESS_CODE) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
