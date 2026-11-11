package com.lp.trade.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.trade.payment.SignatureVerifier;
import com.lp.trade.payment.alipay.AlipayCredentialLoader;
import com.lp.trade.payment.alipay.AlipayCredentials;
import com.lp.trade.payment.alipay.AlipaySignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Registers the RSA2 verifier instead of the failing placeholder when the channel is enabled.
     *
     * @param credentials application id and Alipay public key of this channel
     * @param sellerId merchant seller id every callback must echo
     * @return verifier of Alipay notifications
     * @throws IllegalStateException when the seller id is blank
     */
    @Bean
    public SignatureVerifier alipaySignatureVerifier(
            AlipayCredentials credentials, @Value("${lp.pay.alipay.seller-id:}") String sellerId) {
        return new AlipaySignatureVerifier(credentials, sellerId);
    }
}
