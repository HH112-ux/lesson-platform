package com.lp.auth.entity;

import java.time.LocalDateTime;

/**
 * One account cancellation request stored in {@code user_account_cancellation}.
 *
 * <p>{@code effectiveAt} is calculated when the request is created and never recalculated, so a
 * later configuration change cannot move an already promised deletion date.
 *
 * @author Caspian
 * @since 0.2.0
 */
public class UserAccountCancellation {

    private Long id;

    private Long userId;

    private Integer status;

    private LocalDateTime requestedAt;

    private LocalDateTime effectiveAt;

    private LocalDateTime withdrawnAt;

    private LocalDateTime executedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Returns the request id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.2.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the request id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.2.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the account id.
     *
     * @return {@code sys_user.id}
     * @author Caspian
     * @since 0.2.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the account id.
     *
     * @param userId {@code sys_user.id}
     * @author Caspian
     * @since 0.2.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the request status.
     *
     * @return 0 cooling off, 1 withdrawn, 2 executed
     * @author Caspian
     * @since 0.2.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the request status.
     *
     * @param status request status
     * @author Caspian
     * @since 0.2.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns when the request was created.
     *
     * @return request time
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    /**
     * Sets when the request was created.
     *
     * @param requestedAt request time
     * @author Caspian
     * @since 0.2.0
     */
    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    /**
     * Returns the fixed deletion time.
     *
     * @return deletion time calculated at request time
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    /**
     * Sets the fixed deletion time.
     *
     * @param effectiveAt deletion time calculated at request time
     * @author Caspian
     * @since 0.2.0
     */
    public void setEffectiveAt(LocalDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    /**
     * Returns when the request was withdrawn.
     *
     * @return withdrawal time, or {@code null} when not withdrawn
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    /**
     * Sets when the request was withdrawn.
     *
     * @param withdrawnAt withdrawal time
     * @author Caspian
     * @since 0.2.0
     */
    public void setWithdrawnAt(LocalDateTime withdrawnAt) {
        this.withdrawnAt = withdrawnAt;
    }

    /**
     * Returns when the request was executed.
     *
     * @return execution time, or {@code null} when not executed
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    /**
     * Sets when the request was executed.
     *
     * @param executedAt execution time
     * @author Caspian
     * @since 0.2.0
     */
    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    /**
     * Returns the row creation time.
     *
     * @return creation time
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the row creation time.
     *
     * @param createdAt creation time
     * @author Caspian
     * @since 0.2.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the row update time.
     *
     * @return update time
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the row update time.
     *
     * @param updatedAt update time
     * @author Caspian
     * @since 0.2.0
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
