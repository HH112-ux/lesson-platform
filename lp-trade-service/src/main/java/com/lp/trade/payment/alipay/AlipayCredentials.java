package com.lp.trade.payment.alipay;

/**
 * Credentials of one Alipay application, read from the verified credential file.
 *
 * <p>The record never appears in logs or API responses; only {@code appId} is non sensitive.
 *
 * @param appId application id registered at the channel
 * @param appPrivateKey PKCS#8 application private key used to sign requests
 * @param alipayPublicKey Alipay public key used to verify responses and callbacks
 * @author Caspian
 * @since 0.4.0
 */
public record AlipayCredentials(String appId, String appPrivateKey, String alipayPublicKey) {}
