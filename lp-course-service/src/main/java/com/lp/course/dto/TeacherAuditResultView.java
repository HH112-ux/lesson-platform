package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * Result of an onboarding review decision.
 *
 * @param teacherId {@code teacher_profile.id}
 * @param auditStatus audit status after the decision, {@code 1} approved or {@code 2} rejected
 * @param auditRemark audit remark
 * @param auditedBy operator user id
 * @param auditedAt decision time
 * @author Caspian
 * @since 0.5.0
 */
public record TeacherAuditResultView(
        long teacherId,
        int auditStatus,
        String auditRemark,
        Long auditedBy,
        LocalDateTime auditedAt) {}
