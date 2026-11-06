package com.lp.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One account row shown on the operations console.
 *
 * @param userId primary key
 * @param username login name
 * @param nickname display name
 * @param phone bound phone number, may be {@code null}
 * @param status {@code 1} enabled, {@code 0} disabled
 * @param roles enabled role codes
 * @param createdAt registration time
 * @author Caspian
 * @since 0.2.0
 */
public record AdminUserView(
        long userId,
        String username,
        String nickname,
        String phone,
        Integer status,
        List<String> roles,
        LocalDateTime createdAt) {}
