package com.lp.trade;

import com.lp.common.internal.InternalTokenConfig;
import com.lp.common.web.GlobalExceptionHandler;
import com.lp.trade.config.TradeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Trade domain service entry point on port 8103.
 *
 * <p>Owns cart, coupons, orders, dual-channel payments (Alipay and WeChat Pay) and refunds. Payment
 * callbacks are a high risk area, so every change here needs the extra review and verification
 * defined in {@code AGENTS.md}. Runtime configuration comes from Nacos once the config centre
 * client is wired in, and from the local defaults in {@code application.yml} until then.
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
@MapperScan("com.lp.trade.mapper")
@EnableConfigurationProperties(TradeProperties.class)
@EnableScheduling
@Import({GlobalExceptionHandler.class, InternalTokenConfig.class})
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
