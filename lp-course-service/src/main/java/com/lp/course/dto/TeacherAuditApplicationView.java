package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * Teacher-facing view of the caller's onboarding application.
 *
 * @param teacherId {@code teacher_profile.id}
 * @param auditStatus audit status, {@code 0} pending, {@code 1} approved, {@code 2} rejected
 * @param auditRemark last audit remark, shown to the teacher on rejection
 * @param auditFromStatus status before the last decision
 * @param auditedBy operator user id of the last decision
 * @param auditedAt time of the last decision
 * @param submittedAt time of the latest application
 * @author Caspian
 * @since 0.5.0
 */
public record TeacherAuditApplicationView(
        long teacherId,
        int auditStatus,
        String auditRemark,
        Integer auditFromStatus,
        Long auditedBy,
        LocalDateTime auditedAt,
        LocalDateTime submittedAt) {}
