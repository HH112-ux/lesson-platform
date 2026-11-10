package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One daily settlement batch stored in {@code payment_settle_batch}.
 *
 * <p>A batch closes one bill day of one channel: it sums the channel aggregates and the local
 * aggregates of the payment and refund bills that were already imported and reconciled, then stores
 * the net amount of each side. It never moves money, never books a channel fee and never settles a
 * teacher share. The unique key {@code uk_channel_date} makes closing idempotent: closing the same
 * day again keeps one row and the same primary key, and re-closing a voided day reactivates that
 * row with the new figures, overwriting the close columns and clearing the void columns. Status 1
 * means closed (the bill day is locked); status 2 means voided (the bill day is unlocked again).
 *
 * @author Caspian
 * @since 0.6.2
 */
public class PaymentSettleBatch {

    private Long id;

    private Integer channel;

    private LocalDate billDate;

    private Integer channelPayCount;

    private BigDecimal channelPayAmount;

    private Integer channelRefundCount;

    private BigDecimal channelRefundAmount;

    private Integer localPayCount;

    private BigDecimal localPayAmount;

    private Integer localRefundCount;

    private BigDecimal localRefundAmount;

    private BigDecimal netChannelAmount;

    private BigDecimal netLocalAmount;

    private Integer diffCount;

    private BigDecimal diffAmount;

    private Integer status;

    private Long closedBy;

    private LocalDateTime closedAt;

    private Long voidBy;

    private LocalDateTime voidAt;

    /** Returns the primary key. @return primary key */
    public Long getId() {
        return id;
    }

    /** Sets the primary key. @param id primary key */
    public void setId(Long id) {
        this.id = id;
    }

    /** Returns the payment channel. @return 1 Alipay, 2 WeChat */
    public Integer getChannel() {
        return channel;
    }

    /** Sets the payment channel. @param channel 1 Alipay, 2 WeChat */
    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    /** Returns the bill day in Asia/Shanghai. @return bill day */
    public LocalDate getBillDate() {
        return billDate;
    }

    /** Sets the bill day. @param billDate bill day in Asia/Shanghai */
    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }

    /** Returns the channel payment bill count. @return channel payment count */
    public Integer getChannelPayCount() {
        return channelPayCount;
    }

    /** Sets the channel payment bill count. @param channelPayCount channel payment count */
    public void setChannelPayCount(Integer channelPayCount) {
        this.channelPayCount = channelPayCount;
    }

    /** Returns the channel payment bill amount in yuan. @return channel payment amount */
    public BigDecimal getChannelPayAmount() {
        return channelPayAmount;
    }

    /**
     * Sets the channel payment bill amount in yuan. @param channelPayAmount channel payment amount
     */
    public void setChannelPayAmount(BigDecimal channelPayAmount) {
        this.channelPayAmount = channelPayAmount;
    }

    /** Returns the channel refund bill count. @return channel refund count */
    public Integer getChannelRefundCount() {
        return channelRefundCount;
    }

    /** Sets the channel refund bill count. @param channelRefundCount channel refund count */
    public void setChannelRefundCount(Integer channelRefundCount) {
        this.channelRefundCount = channelRefundCount;
    }

    /** Returns the channel refund bill amount in yuan. @return channel refund amount */
    public BigDecimal getChannelRefundAmount() {
        return channelRefundAmount;
    }

    /**
     * Sets the channel refund bill amount in yuan. @param channelRefundAmount channel refund amount
     */
    public void setChannelRefundAmount(BigDecimal channelRefundAmount) {
        this.channelRefundAmount = channelRefundAmount;
    }

    /** Returns the local payment count. @return local payment count */
    public Integer getLocalPayCount() {
        return localPayCount;
    }

    /** Sets the local payment count. @param localPayCount local payment count */
    public void setLocalPayCount(Integer localPayCount) {
        this.localPayCount = localPayCount;
    }

    /** Returns the local payment amount in yuan. @return local payment amount */
    public BigDecimal getLocalPayAmount() {
        return localPayAmount;
    }

    /** Sets the local payment amount in yuan. @param localPayAmount local payment amount */
    public void setLocalPayAmount(BigDecimal localPayAmount) {
        this.localPayAmount = localPayAmount;
    }

    /** Returns the local refund count. @return local refund count */
    public Integer getLocalRefundCount() {
        return localRefundCount;
    }

    /** Sets the local refund count. @param localRefundCount local refund count */
    public void setLocalRefundCount(Integer localRefundCount) {
        this.localRefundCount = localRefundCount;
    }

    /** Returns the local refund amount in yuan. @return local refund amount */
    public BigDecimal getLocalRefundAmount() {
        return localRefundAmount;
    }

    /** Sets the local refund amount in yuan. @param localRefundAmount local refund amount */
    public void setLocalRefundAmount(BigDecimal localRefundAmount) {
        this.localRefundAmount = localRefundAmount;
    }

    /** Returns the channel net amount (payment minus refund) in yuan. @return channel net amount */
    public BigDecimal getNetChannelAmount() {
        return netChannelAmount;
    }

    /** Sets the channel net amount in yuan. @param netChannelAmount channel net amount */
    public void setNetChannelAmount(BigDecimal netChannelAmount) {
        this.netChannelAmount = netChannelAmount;
    }

    /** Returns the local net amount (payment minus refund) in yuan. @return local net amount */
    public BigDecimal getNetLocalAmount() {
        return netLocalAmount;
    }

    /** Sets the local net amount in yuan. @param netLocalAmount local net amount */
    public void setNetLocalAmount(BigDecimal netLocalAmount) {
        this.netLocalAmount = netLocalAmount;
    }

    /** Returns the difference count of both bills. @return difference count */
    public Integer getDiffCount() {
        return diffCount;
    }

    /** Sets the difference count of both bills. @param diffCount difference count */
    public void setDiffCount(Integer diffCount) {
        this.diffCount = diffCount;
    }

    /** Returns the summed difference amount in yuan. @return difference amount */
    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    /** Sets the summed difference amount in yuan. @param diffAmount difference amount */
    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
    }

    /** Returns the batch status. @return 1 closed, 2 voided */
    public Integer getStatus() {
        return status;
    }

    /** Sets the batch status. @param status 1 closed, 2 voided */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /** Returns the operator who closed the batch. @return operator user id */
    public Long getClosedBy() {
        return closedBy;
    }

    /** Sets the operator who closed the batch. @param closedBy operator user id */
    public void setClosedBy(Long closedBy) {
        this.closedBy = closedBy;
    }

    /** Returns when the batch was closed. @return close time */
    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    /** Sets when the batch was closed. @param closedAt close time */
    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    /** Returns the operator who voided the batch. @return operator user id or {@code null} */
    public Long getVoidBy() {
        return voidBy;
    }

    /** Sets the operator who voided the batch. @param voidBy operator user id */
    public void setVoidBy(Long voidBy) {
        this.voidBy = voidBy;
    }

    /** Returns when the batch was voided. @return void time or {@code null} */
    public LocalDateTime getVoidAt() {
        return voidAt;
    }

    /** Sets when the batch was voided. @param voidAt void time */
    public void setVoidAt(LocalDateTime voidAt) {
        this.voidAt = voidAt;
    }
}
