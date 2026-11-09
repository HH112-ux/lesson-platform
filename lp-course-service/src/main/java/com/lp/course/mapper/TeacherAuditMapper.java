package com.lp.course.mapper;

import com.lp.course.entity.TeacherProfileAudit;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Teacher onboarding audit statements.
 *
 * <p>The audit columns stay on {@code teacher_profile} until the unified audit table is introduced;
 * each decision keeps the operator, the time and the status before and after it.
 *
 * @author Caspian
 * @since 0.5.0
 */
@Mapper
public interface TeacherAuditMapper {

    /** Projection shared by the single row and page queries. */
    String SELECT_PROJECTION =
            "SELECT t.id, t.user_id AS userId, t.display_name AS displayName, "
                    + "t.avatar_url AS avatarUrl, t.bio, t.status, "
                    + "t.audit_status AS auditStatus, t.audit_remark AS auditRemark, "
                    + "t.audit_from_status AS auditFromStatus, t.audited_by AS auditedBy, "
                    + "t.audited_at AS auditedAt, t.submitted_at AS submittedAt, "
                    + "t.created_at AS createdAt, "
                    + "(SELECT COUNT(*) FROM course c "
                    + "WHERE c.owner_teacher_id = t.id AND c.deleted = 0) AS courseCount "
                    + "FROM teacher_profile t ";

    /**
     * Counts teacher profiles matching the review filters.
     *
     * @param auditStatus optional audit status filter
     * @param keyword optional match on the display name
     * @return matching rows
     * @author Caspian
     * @since 0.5.0
     */
    @Select(
            "<script>SELECT COUNT(*) FROM teacher_profile WHERE deleted = 0 "
                    + "<if test='auditStatus != null'>AND audit_status = #{auditStatus} </if>"
                    + "<if test='keyword != null and keyword != \"\"'>"
                    + "AND display_name LIKE CONCAT('%', #{keyword}, '%') </if></script>")
    long count(@Param("auditStatus") Integer auditStatus, @Param("keyword") String keyword);

    /**
     * Lists one page of teacher profiles with the live course count.
     *
     * @param auditStatus optional audit status filter
     * @param keyword optional match on the display name
     * @param offset zero based row offset
     * @param limit maximum rows
     * @return profile rows, newest first
     * @author Caspian
     * @since 0.5.0
     */
    @Select(
            "<script>"
                    + SELECT_PROJECTION
                    + "WHERE t.deleted = 0 "
                    + "<if test='auditStatus != null'>AND t.audit_status = #{auditStatus} </if>"
                    + "<if test='keyword != null and keyword != \"\"'>"
                    + "AND t.display_name LIKE CONCAT('%', #{keyword}, '%') </if>"
                    + "ORDER BY t.created_at DESC, t.id DESC "
                    + "LIMIT #{limit} OFFSET #{offset}</script>")
    List<TeacherProfileAudit> selectPage(
            @Param("auditStatus") Integer auditStatus,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * Loads one profile by its primary key.
     *
     * @param teacherId {@code teacher_profile.id}
     * @return profile row, or {@code null} when absent or deleted
     * @author Caspian
     * @since 0.5.0
     */
    @Select(SELECT_PROJECTION + "WHERE t.id = #{teacherId} AND t.deleted = 0 LIMIT 1")
    TeacherProfileAudit selectById(@Param("teacherId") long teacherId);

    /**
     * Loads one profile by its bound account.
     *
     * @param userId authenticated user id
     * @return profile row, or {@code null} when the account has no profile
     * @author Caspian
     * @since 0.5.0
     */
    @Select(
            SELECT_PROJECTION
                    + "WHERE t.user_id = #{userId} AND t.deleted = 0 ORDER BY t.id LIMIT 1")
    TeacherProfileAudit selectByUserId(@Param("userId") long userId);

    /**
     * Applies an approval or rejection to a pending profile.
     *
     * @param teacherId profile primary key
     * @param toStatus resulting audit status, {@code 1} approved or {@code 2} rejected
     * @param remark audit remark, required on rejection
     * @param reviewerId operator user id
     * @param auditedAt decision time
     * @return affected rows, {@code 0} when the profile is no longer pending
     * @author Caspian
     * @since 0.5.0
     */
    @Update(
            "UPDATE teacher_profile SET audit_from_status = audit_status, "
                    + "audit_status = #{toStatus}, audit_remark = #{remark}, "
                    + "audited_by = #{reviewerId}, audited_at = #{auditedAt} "
                    + "WHERE id = #{teacherId} AND deleted = 0 AND audit_status = 0")
    int applyReview(
            @Param("teacherId") long teacherId,
            @Param("toStatus") int toStatus,
            @Param("remark") String remark,
            @Param("reviewerId") long reviewerId,
            @Param("auditedAt") LocalDateTime auditedAt);

    /**
     * Puts a rejected profile back into the review queue.
     *
     * <p>Pending profiles may resubmit idempotently; approved profiles never match, because a
     * granted review cannot be reopened by the teacher.
     *
     * @param userId authenticated user id
     * @param submittedAt application time
     * @return affected rows, {@code 0} when the profile is absent or already approved
     * @author Caspian
     * @since 0.5.0
     */
    @Update(
            "UPDATE teacher_profile SET audit_from_status = audit_status, audit_status = 0, "
                    + "audit_remark = NULL, audited_by = NULL, audited_at = NULL, "
                    + "submitted_at = #{submittedAt} "
                    + "WHERE user_id = #{userId} AND deleted = 0 AND audit_status IN (0, 2)")
    int resubmit(@Param("userId") long userId, @Param("submittedAt") LocalDateTime submittedAt);
}
