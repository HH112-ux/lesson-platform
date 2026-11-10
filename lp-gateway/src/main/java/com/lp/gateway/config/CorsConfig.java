package com.lp.gateway.config;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Registers the browser origin policy as a {@link CorsWebFilter}.
 *
 * <p>A web filter runs before route matching and before every gateway {@code GlobalFilter}, so a
 * preflight {@code OPTIONS} request is answered here and never reaches {@code JwtAuthFilter}; no
 * identity header can be injected into it and no downstream service is called.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    /** Origins used only when the dev profile is active and no origin is configured. */
    static final List<String> DEV_DEFAULT_ORIGINS =
            List.of("http://localhost:5173", "http://localhost:5174");

    /** Methods a browser client may use against the API. */
    static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /**
     * Request headers the browser may send.
     *
     * <p>{@code Idempotency-Key} is required by order, payment and refund endpoints; {@code
     * X-Request-Id} and {@code X-Device-Id} are client supplied correlation and device hints.
     */
    static final List<String> ALLOWED_HEADERS =
            List.of(
                    "Authorization",
                    "Content-Type",
                    "Accept",
                    "Origin",
                    "Idempotency-Key",
                    "X-Request-Id",
                    "X-Device-Id",
                    "X-Auth-Timestamp",
                    "X-Auth-Signature");

    /** Response headers a browser script may read. */
    static final List<String> EXPOSED_HEADERS = List.of("X-Request-Id");

    /**
     * Creates the reactive CORS filter.
     *
     * @param properties bound origin settings
     * @param environment active profile source
     * @return filter registered for every path
     * @throws IllegalStateException when the origin policy is invalid for the active profile
     * @author Caspian
     * @since 0.1.0
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "lp.cors",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public CorsWebFilter corsWebFilter(CorsProperties properties, Environment environment) {
        CorsConfiguration configuration =
                buildConfiguration(
                        properties.getAllowedOrigins(),
                        environment.acceptsProfiles(Profiles.of("dev")),
                        properties.getMaxAgeSeconds());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }

    /**
     * Validates the configured origins and builds the CORS configuration.
     *
     * @param configuredOrigins origins from configuration, possibly empty
     * @param devProfile whether the dev profile is active
     * @param maxAgeSeconds preflight cache duration
     * @return validated configuration with credentials enabled
     * @throws IllegalStateException when an origin is a wildcard, or the list is empty outside dev
     * @author Caspian
     * @since 0.1.0
     */
    static CorsConfiguration buildConfiguration(
            List<String> configuredOrigins, boolean devProfile, long maxAgeSeconds) {
        List<String> origins =
                configuredOrigins == null
                        ? List.of()
                        : configuredOrigins.stream()
                                .filter(origin -> origin != null && !origin.isBlank())
                                .map(String::trim)
                                .toList();
        if (origins.isEmpty()) {
            if (!devProfile) {
                throw new IllegalStateException(
                        "lp.cors.allowed-origins must not be empty outside the dev profile");
            }
            origins = DEV_DEFAULT_ORIGINS;
        }
        for (String origin : origins) {
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "lp.cors.allowed-origins must not contain '*' when credentials are"
                                + " allowed");
            }
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(maxAgeSeconds);
        return configuration;
    }
}
