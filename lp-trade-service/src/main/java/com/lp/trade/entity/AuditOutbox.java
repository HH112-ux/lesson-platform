package com.lp.trade.entity;

import java.time.LocalDateTime;

/**
 * One pending unified-audit delivery intent stored in {@code audit_outbox}.
 *
 * <p>A refund decision updates {@code refund_request} in {@code lp_oms}, while the unified audit
 * table lives in {@code lp_cms}. The decision transaction writes this row together with the status
 * change; the relay later posts it to course-service. The row therefore carries every column the
 * audit entry needs, so delivery needs no second read of the refund.
 *
 * @author Caspian
 * @since 0.6.0
 */
public class AuditOutbox {

    private Long id;

    private String dedupeKey;

    private Long operatorId;

    private Integer operatorRole;

    private Integer actionType;

    private Integer objectType;

    private Long objectId;

    private Integer fromStatus;

    private Integer toStatus;

    private String remark;

    private String traceId;

    private LocalDateTime occurredAt;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime deliveredAt;

    private String lastError;

    /**
     * Returns the outbox row id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.6.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the outbox row id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.6.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the delivery idempotency key.
     *
     * @return key shared by every attempt of the same decision
     * @author Caspian
     * @since 0.6.0
     */
    public String getDedupeKey() {
        return dedupeKey;
    }

    /**
     * Sets the delivery idempotency key.
     *
     * @param dedupeKey key shared by every attempt of the same decision
     * @author Caspian
     * @since 0.6.0
     */
    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
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
     * @return 2 refund approve, 3 refund reject
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getActionType() {
        return actionType;
    }

    /**
     * Sets the action code.
     *
     * @param actionType 2 refund approve, 3 refund reject, 6 settle batch void
     * @author Caspian
     * @since 0.6.0
     */
    public void setActionType(Integer actionType) {
        this.actionType = actionType;
    }

    /**
     * Returns the object type code.
     *
     * @return 1 refund, 3 settle batch
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getObjectType() {
        return objectType;
    }

    /**
     * Sets the object type code.
     *
     * @param objectType 1 refund, 3 settle batch
     * @author Caspian
     * @since 0.6.0
     */
    public void setObjectType(Integer objectType) {
        this.objectType = objectType;
    }

    /**
     * Returns the refund primary key.
     *
     * @return refund primary key
     * @author Caspian
     * @since 0.6.0
     */
    public Long getObjectId() {
        return objectId;
    }

    /**
     * Sets the refund primary key.
     *
     * @param objectId refund primary key
     * @author Caspian
     * @since 0.6.0
     */
    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    /**
     * Returns the status before the decision.
     *
     * @return status before the decision, always 0 pending
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
     * @return 1 approved, 3 rejected
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getToStatus() {
        return toStatus;
    }

    /**
     * Sets the status after the decision.
     *
     * @param toStatus 1 approved, 3 rejected
     * @author Caspian
     * @since 0.6.0
     */
    public void setToStatus(Integer toStatus) {
        this.toStatus = toStatus;
    }

    /**
     * Returns the rejection reason.
     *
     * @return reason text, {@code null} on approval
     * @author Caspian
     * @since 0.6.0
     */
    public String getRemark() {
        return remark;
    }

    /**
     * Sets the rejection reason.
     *
     * @param remark reason text
     * @author Caspian
     * @since 0.6.0
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * Returns the request trace id.
     *
     * @return trace id of the review request
     * @author Caspian
     * @since 0.6.0
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Sets the request trace id.
     *
     * @param traceId trace id of the review request
     * @author Caspian
     * @since 0.6.0
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * Returns the decision time.
     *
     * @return decision time
     * @author Caspian
     * @since 0.6.0
     */
    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    /**
     * Sets the decision time.
     *
     * @param occurredAt decision time
     * @author Caspian
     * @since 0.6.0
     */
    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    /**
     * Returns the delivery status.
     *
     * @return 0 pending, 1 delivered, 2 given up
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the delivery status.
     *
     * @param status 0 pending, 1 delivered, 2 given up
     * @author Caspian
     * @since 0.6.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the failed attempt count.
     *
     * @return failed attempts so far
     * @author Caspian
     * @since 0.6.0
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * Sets the failed attempt count.
     *
     * @param retryCount failed attempts so far
     * @author Caspian
     * @since 0.6.0
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * Returns the next attempt time.
     *
     * @return next attempt time
     * @author Caspian
     * @since 0.6.0
     */
    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    /**
     * Sets the next attempt time.
     *
     * @param nextRetryAt next attempt time
     * @author Caspian
     * @since 0.6.0
     */
    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * Returns the delivery time.
     *
     * @return delivery time, {@code null} while pending
     * @author Caspian
     * @since 0.6.0
     */
    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    /**
     * Sets the delivery time.
     *
     * @param deliveredAt delivery time
     * @author Caspian
     * @since 0.6.0
     */
    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    /**
     * Returns the last failure reason.
     *
     * @return last failure reason, {@code null} when no attempt failed
     * @author Caspian
     * @since 0.6.0
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Sets the last failure reason.
     *
     * @param lastError last failure reason
     * @author Caspian
     * @since 0.6.0
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
