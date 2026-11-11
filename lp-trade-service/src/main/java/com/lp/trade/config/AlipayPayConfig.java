package com.lp.trade.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.trade.payment.alipay.AlipayCredentialLoader;
import com.lp.trade.payment.alipay.AlipayCredentials;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the Alipay channel when {@code lp.pay.alipay.enabled} is set.
 *
 * <p>The credentials bean is created eagerly so an unreadable or incomplete credential file stops
 * the boot instead of failing later on the first payment.
 *
 * @author Caspian
 * @since 0.4.0
 */
@Configuration
@EnableConfigurationProperties(AlipayPayProperties.class)
@ConditionalOnProperty(prefix = "lp.pay.alipay", name = "enabled", havingValue = "true")
public class AlipayPayConfig {

    /**
     * Reads and validates the verified credential file once per process.
     *
     * @param properties channel settings holding the credential file path
     * @param environment application environment used for the profile guard
     * @param objectMapper JSON reader of the credential file
     * @return validated credentials shared by the channel adapters
     * @throws IllegalStateException when the file is missing, unreadable or incomplete
     * @author Caspian
     * @since 0.4.0
     */
    @Bean
    public AlipayCredentials alipayCredentials(
            AlipayPayProperties properties, Environment environment, ObjectMapper objectMapper) {
        return new AlipayCredentialLoader(properties, environment, objectMapper).load();
    }
}
