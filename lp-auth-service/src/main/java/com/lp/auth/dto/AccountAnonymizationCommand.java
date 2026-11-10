package com.lp.auth.dto;

/**
 * Request sent to the trade-service anonymisation endpoint.
 *
 * <p>The salt travels in the body rather than the query string so it never reaches an access log.
 *
 * @param userId account owner
 * @param salt secret salt mixed into the digest
 * @author Caspian
 * @since 0.3.0
 */
public record AccountAnonymizationCommand(Long userId, String salt) {}
