package com.lp.course.entity;

import java.time.LocalDateTime;

/**
 * One immutable operations audit entry stored in {@code admin_audit_log}.
 *
 * <p>The table is append-only. A row is written by the three review actions covered by L1-7: course
 * review, refund review and teacher review. The before and after status values are interpreted by
 * {@code objectType}, so the same column pair serves all three domains.
 *
 * @author Caspian
 * @since 0.6.0
 */
public class AdminAuditLog {

    private Long id;

    private Long operatorId;

    private Integer operatorRole;

    private Integer actionType;

    private Integer objectType;

    private Long objectId;

    private Integer fromStatus;

    private Integer toStatus;

    private String remark;

    private String traceId;

    private String dedupeKey;

    private LocalDateTime createdAt;

    /**
     * Returns the audit row id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.6.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the audit row id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.6.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the operator user id.
     *
     * @return operator user id
     * @author Caspian
     * @since 0.6.0
     */
    public Long getOperatorId() {
        return operatorId;
    }

    /**
     * Sets the operator user id.
     *
     * @param operatorId operator user id
     * @author Caspian
     * @since 0.6.0
     */
    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    /**
     * Returns the operator role code.
     *
     * @return 0 operations, 1 administrator
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getOperatorRole() {
        return operatorRole;
    }

    /**
     * Sets the operator role code.
     *
     * @param operatorRole 0 operations, 1 administrator
     * @author Caspian
     * @since 0.6.0
     */
    public void setOperatorRole(Integer operatorRole) {
        this.operatorRole = operatorRole;
    }

    /**
     * Returns the action code.
     *
     * @return 0 to 5, see {@code com.lp.common.audit.AuditAction}
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getActionType() {
        return actionType;
    }

    /**
     * Sets the action code.
     *
     * @param actionType 0 to 5, see {@code com.lp.common.audit.AuditAction}
     * @author Caspian
     * @since 0.6.0
     */
    public void setActionType(Integer actionType) {
        this.actionType = actionType;
    }

    /**
     * Returns the object type code.
     *
     * @return 0 course, 1 refund, 2 teacher
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getObjectType() {
        return objectType;
    }

    /**
     * Sets the object type code.
     *
     * @param objectType 0 course, 1 refund, 2 teacher
     * @author Caspian
     * @since 0.6.0
     */
    public void setObjectType(Integer objectType) {
        this.objectType = objectType;
    }

    /**
     * Returns the reviewed object id.
     *
     * @return primary key of the reviewed row
     * @author Caspian
     * @since 0.6.0
     */
    public Long getObjectId() {
        return objectId;
    }

    /**
     * Sets the reviewed object id.
     *
     * @param objectId primary key of the reviewed row
     * @author Caspian
     * @since 0.6.0
     */
    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    /**
     * Returns the status before the decision.
     *
     * @return status before the decision
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getFromStatus() {
        return fromStatus;
    }

    /**
     * Sets the status before the decision.
     *
     * @param fromStatus status before the decision
     * @author Caspian
     * @since 0.6.0
     */
    public void setFromStatus(Integer fromStatus) {
        this.fromStatus = fromStatus;
    }

    /**
     * Returns the status after the decision.
     *
     * @return status after the decision
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getToStatus() {
        return toStatus;
    }

    /**
     * Sets the status after the decision.
     *
     * @param toStatus status after the decision
     * @author Caspian
     * @since 0.6.0
     */
    public void setToStatus(Integer toStatus) {
        this.toStatus = toStatus;
    }

    /**
     * Returns the remark or rejection reason.
     *
     * @return remark text, {@code null} when the decision carries none
     * @author Caspian
     * @since 0.6.0
     */
    public String getRemark() {
        return remark;
    }

    /**
     * Sets the remark or rejection reason.
     *
     * @param remark remark text
     * @author Caspian
     * @since 0.6.0
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * Returns the request trace id.
     *
     * @return trace id used to correlate business logs
     * @author Caspian
     * @since 0.6.0
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Sets the request trace id.
     *
     * @param traceId trace id used to correlate business logs
     * @author Caspian
     * @since 0.6.0
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * Returns the delivery idempotency key.
     *
     * @return key supplied by the cross-service relay, {@code null} for local writes
     * @author Caspian
     * @since 0.6.0
     */
    public String getDedupeKey() {
        return dedupeKey;
    }

    /**
     * Sets the delivery idempotency key.
     *
     * @param dedupeKey key supplied by the cross-service relay
     * @author Caspian
     * @since 0.6.0
     */
    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    /**
     * Returns the decision time.
     *
     * @return decision time
     * @author Caspian
     * @since 0.6.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the decision time.
     *
     * @param createdAt decision time
     * @author Caspian
     * @since 0.6.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
