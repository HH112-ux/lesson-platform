package com.lp.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One course waiting for the operations review decision.
 *
 * @param courseId course primary key
 * @param title course title
 * @param coverUrl cover address
 * @param ownerTeacherId teacher that owns the course
 * @param price current price in yuan
 * @param submittedAt time the teacher submitted the course
 * @author Caspian
 * @since 0.1.0
 */
public record AdminPendingCourseView(
        long courseId,
        String title,
        String coverUrl,
        long ownerTeacherId,
        BigDecimal price,
        LocalDateTime submittedAt) {}
