package com.lp.course.dto;

import java.time.LocalDateTime;

/**
 * One teacher profile as shown in the operations onboarding review queue.
 *
 * @param teacherId {@code teacher_profile.id}
 * @param userId bound account id, may be {@code null} for seed rows
 * @param displayName public teacher name
 * @param avatarUrl avatar URL
 * @param bio teacher bio
 * @param status account enable flag, {@code 1} enabled
 * @param auditStatus onboarding audit status, {@code 0} pending, {@code 1} approved, {@code 2}
 *     rejected
 * @param auditRemark last audit remark, required on rejection
 * @param auditedBy operator user id of the last decision
 * @param auditedAt time of the last decision
 * @param submittedAt time of the latest application
 * @param courseCount live courses owned by the teacher
 * @param createdAt profile creation time
 * @author Caspian
 * @since 0.5.0
 */
public record TeacherAuditView(
        long teacherId,
        Long userId,
        String displayName,
        String avatarUrl,
        String bio,
        int status,
        int auditStatus,
        String auditRemark,
        Long auditedBy,
        LocalDateTime auditedAt,
        LocalDateTime submittedAt,
        long courseCount,
        LocalDateTime createdAt) {}
