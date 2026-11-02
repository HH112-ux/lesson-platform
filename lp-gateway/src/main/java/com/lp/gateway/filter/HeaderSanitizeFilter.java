package com.lp.gateway.filter;

import com.lp.gateway.support.HeaderSanitizer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Strips inbound identity headers before any route is served.
 *
 * <p>Client supplied {@code X-User-Id}, {@code X-User-Roles}, {@code X-User-Source},
 * {@code X-Tenant-Id} and personal data variants are dropped regardless of casing or
 * duplication. Injection of trusted values happens later, in the JWT filter, so a downstream
 * service can never observe client supplied identity.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class HeaderSanitizeFilter implements GlobalFilter, Ordered {

    /** Runs before authentication and route filters. */
    public static final int ORDER = -100;

    /**
     * Replaces inbound headers with the sanitized copy and continues the chain.
     *
     * @param exchange current exchange
     * @param chain    remaining gateway filter chain
     * @return completion signal of the downstream chain
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.clear();
                    headers.addAll(HeaderSanitizer.sanitize(exchange.getRequest().getHeaders()));
                })
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    /**
     * Returns the filter order; lower values run first.
     *
     * @return {@link #ORDER}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public int getOrder() {
        return ORDER;
    }
}
