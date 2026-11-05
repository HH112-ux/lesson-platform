package com.lp.gateway.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

/**
 * Verifies the gateway route table declared in {@code application.yml}.
 *
 * <p>Routes are bound exactly as Spring Cloud Gateway binds them, then each Path predicate is
 * evaluated against sample requests. This covers both configuration wiring and matching, so a
 * renamed prefix or a dropped route fails the build.
 *
 * @author Caspian
 * @since 0.1.0
 */
class GatewayRouteConfigurationTest {

    /** Domain prefix and sample path expected to be routed to each domain service. */
    private static final Map<String, String> DOMAIN_SAMPLES =
            Map.of(
                    "auth-service", "/api/v1/user/profile",
                    "course-service", "/api/v1/course/lessons/1",
                    "trade-service", "/api/v1/trade/orders/1",
                    "learn-service", "/api/v1/learning/my-courses",
                    "ugc-service", "/api/v1/ugc/comments/1",
                    "admin-service", "/api/v1/admin/audits/1",
                    "ai-service", "/api/v1/ai/status");

    /** Expected route count: seven domain services plus the miniapp segment. */
    private static final int EXPECTED_ROUTE_COUNT = 8;

    private static GatewayProperties properties;

    /**
     * Loads the gateway configuration with the same binder Spring Boot uses.
     *
     * @throws IOException when {@code application.yml} cannot be read
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeAll
    static void loadRoutes() throws IOException {
        GenericApplicationContext context = new GenericApplicationContext();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> source :
                loader.load("application.yml", new ClassPathResource("application.yml"))) {
            context.getEnvironment().getPropertySources().addLast(source);
        }
        properties =
                Binder.get(context.getEnvironment())
                        .bind("spring.cloud.gateway", GatewayProperties.class)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "gateway routes are not configured"));
    }

    /**
     * Binds every route with its expected id, default URI and domain segment.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void declaresOneRoutePerDomainService() {
        assertThat(properties.getRoutes()).hasSize(EXPECTED_ROUTE_COUNT);

        for (Map.Entry<String, String> sample : DOMAIN_SAMPLES.entrySet()) {
            RouteDefinition route = routeById(sample.getKey());
            assertThat(pathPatterns(route)).contains(domainPattern(sample.getValue()));
            assertThat(route.getUri()).hasToString(defaultUriOf(sample.getKey()));
        }
    }

    /**
     * Matches each domain prefix to exactly its own service route.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void matchesDomainPrefixToItsOwnRoute() {
        for (Map.Entry<String, String> sample : DOMAIN_SAMPLES.entrySet()) {
            RouteDefinition route = routeById(sample.getKey());
            Predicate<ServerWebExchange> predicate = pathPredicate(route);

            assertThat(predicate.test(exchange(sample.getValue()))).isTrue();
            for (Map.Entry<String, String> other : DOMAIN_SAMPLES.entrySet()) {
                if (!other.getKey().equals(sample.getKey())) {
                    assertThat(predicate.test(exchange(other.getValue())))
                            .as("route %s must not match %s", sample.getKey(), other.getValue())
                            .isFalse();
                }
            }
        }
    }

    /**
     * Rejects an unknown prefix with every configured route predicate.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsUnknownPrefix() {
        ServerWebExchange unknown = exchange("/api/v1/unknown/ping");

        for (RouteDefinition route : properties.getRoutes()) {
            assertThat(pathPredicate(route).test(unknown))
                    .as("route %s must not match an unknown prefix", route.getId())
                    .isFalse();
        }
    }

    /**
     * Finds a route by id.
     *
     * @param id route id
     * @return the matching route definition
     * @author Caspian
     * @since 0.1.0
     */
    private static RouteDefinition routeById(String id) {
        return properties.getRoutes().stream()
                .filter(route -> id.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing route " + id));
    }

    /**
     * Extracts all Path predicate patterns of a route.
     *
     * @param route route definition
     * @return immutable list of Ant style path patterns
     * @author Caspian
     * @since 0.1.0
     */
    private static List<String> pathPatterns(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equalsIgnoreCase(predicate.getName()))
                .flatMap(
                        predicate ->
                                predicate.getArgs().entrySet().stream()
                                        .sorted(Map.Entry.comparingByKey())
                                        .map(Map.Entry::getValue))
                .toList();
    }

    /**
     * Builds the Path predicate of a route without starting a web server.
     *
     * @param route route definition
     * @return predicate that evaluates the route Path patterns
     * @author Caspian
     * @since 0.1.0
     */
    private static Predicate<ServerWebExchange> pathPredicate(RouteDefinition route) {
        PathRoutePredicateFactory factory = new PathRoutePredicateFactory();
        PathRoutePredicateFactory.Config config = new PathRoutePredicateFactory.Config();
        config.setPatterns(pathPatterns(route));
        return factory.apply(config);
    }

    /**
     * Creates a GET exchange for the given path.
     *
     * @param path request path
     * @return mock reactive exchange
     * @author Caspian
     * @since 0.1.0
     */
    private static ServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    /**
     * Derives the domain Ant pattern from a sample path.
     *
     * @param samplePath concrete sample path under the domain segment
     * @return pattern such as {@code /api/v1/user/**}
     * @author Caspian
     * @since 0.1.0
     */
    private static String domainPattern(String samplePath) {
        String segment = samplePath.substring("/api/v1/".length());
        return "/api/v1/" + segment.substring(0, segment.indexOf('/')) + "/**";
    }

    /**
     * Returns the documented default URI of a domain service.
     *
     * @param routeId route id, equal to the service name
     * @return default URI from the engineering plan port table
     * @author Caspian
     * @since 0.1.0
     */
    private static String defaultUriOf(String routeId) {
        return switch (routeId) {
            case "auth-service" -> "http://localhost:8101";
            case "course-service" -> "http://localhost:8102";
            case "trade-service" -> "http://localhost:8103";
            case "learn-service" -> "http://localhost:8104";
            case "ugc-service" -> "http://localhost:8105";
            case "admin-service" -> "http://localhost:8106";
            case "ai-service" -> "http://localhost:8107";
            default -> throw new AssertionError("unexpected route " + routeId);
        };
    }
}
