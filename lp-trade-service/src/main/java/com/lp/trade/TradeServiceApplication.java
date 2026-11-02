package com.lp.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Trade domain service entry point on port 8103.
 *
 * <p>Owns cart, coupons, orders, dual-channel payments (Alipay and WeChat Pay) and refunds.
 * Payment callbacks are a high risk area, so every change here needs the extra review and
 * verification defined in {@code AGENTS.md}. The skeleton exposes health only; runtime
 * configuration comes from Nacos once the config centre client is wired in, and from the local
 * defaults in {@code application.yml} until then.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
public class TradeServiceApplication {

    /**
     * Starts the trade service.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(TradeServiceApplication.class, args);
    }
}
