package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One daily reconciliation aggregate stored in {@code payment_reconcile}.
 *
 * <p>The unique key {@code uk_channel_date_type} makes an import an upsert: importing the same
 * channel, bill day and bill type again overwrites the row instead of adding a second one. The raw
 * CSV text is kept so a recheck can recompute the differences without the original file, and the
 * difference list is stored as JSON in {@code diff_detail} instead of an object store. Status 2
 * means unresolved differences exist and is reported as {@code 50204} on a settlement precondition
 * check; status 3 means operations reviewed them. This row never moves money.
 *
 * @author Caspian
 * @since 0.6.1
 */
public class PaymentReconcile {

    private Long id;

    private Integer channel;

    private LocalDate billDate;

    private Integer billType;

    private Integer channelCount;

    private BigDecimal channelAmount;

    private Integer localCount;

    private BigDecimal localAmount;

    private Integer diffCount;

    private BigDecimal diffAmount;

    private Integer status;

    private Long handledBy;

    private LocalDateTime handledAt;

    private String rawBill;

    private String diffDetail;

    /** Returns the primary key. @return primary key */
    public Long getId() {
        return id;
    }

    /** Sets the primary key. @param id primary key */
    public void setId(Long id) {
        this.id = id;
    }

    /** Returns the payment channel. @return channel code */
    public Integer getChannel() {
        return channel;
    }

    /** Sets the payment channel. @param channel channel code */
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

    /** Returns the bill type. @return 1 payment, 2 refund */
    public Integer getBillType() {
        return billType;
    }

    /** Sets the bill type. @param billType 1 payment, 2 refund */
    public void setBillType(Integer billType) {
        this.billType = billType;
    }

    /** Returns the channel line count. @return channel line count */
    public Integer getChannelCount() {
        return channelCount;
    }

    /** Sets the channel line count. @param channelCount channel line count */
    public void setChannelCount(Integer channelCount) {
        this.channelCount = channelCount;
    }

    /** Returns the channel amount in yuan. @return channel amount */
    public BigDecimal getChannelAmount() {
        return channelAmount;
    }

    /** Sets the channel amount in yuan. @param channelAmount channel amount */
    public void setChannelAmount(BigDecimal channelAmount) {
        this.channelAmount = channelAmount;
    }

    /** Returns the local record count. @return local record count */
    public Integer getLocalCount() {
        return localCount;
    }

    /** Sets the local record count. @param localCount local record count */
    public void setLocalCount(Integer localCount) {
        this.localCount = localCount;
    }

    /** Returns the local amount in yuan. @return local amount */
    public BigDecimal getLocalAmount() {
        return localAmount;
    }

    /** Sets the local amount in yuan. @param localAmount local amount */
    public void setLocalAmount(BigDecimal localAmount) {
        this.localAmount = localAmount;
    }

    /** Returns the difference count. @return difference count */
    public Integer getDiffCount() {
        return diffCount;
    }

    /** Sets the difference count. @param diffCount difference count */
    public void setDiffCount(Integer diffCount) {
        this.diffCount = diffCount;
    }

    /**
     * Returns the difference amount in yuan. @return difference amount, positive when channel
     * collected more
     */
    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    /**
     * Sets the difference amount in yuan. @param diffAmount difference amount, positive when
     * channel collected more
     */
    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
    }

    /** Returns the reconciliation status. @return 0 reconciling, 1 matched, 2 pending, 3 handled */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the reconciliation status. @param status 0 reconciling, 1 matched, 2 pending, 3 handled
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the operator who marked differences handled. @return operator user id or {@code null}
     */
    public Long getHandledBy() {
        return handledBy;
    }

    /** Sets the operator who marked differences handled. @param handledBy operator user id */
    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

    /** Returns when differences were marked handled. @return handled time or {@code null} */
    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    /** Sets when differences were marked handled. @param handledAt handled time */
    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    /** Returns the raw imported CSV text. @return raw bill text */
    public String getRawBill() {
        return rawBill;
    }

    /** Sets the raw imported CSV text. @param rawBill raw bill text */
    public void setRawBill(String rawBill) {
        this.rawBill = rawBill;
    }

    /** Returns the difference list JSON. @return difference JSON */
    public String getDiffDetail() {
        return diffDetail;
    }

    /** Sets the difference list JSON. @param diffDetail difference JSON */
    public void setDiffDetail(String diffDetail) {
        this.diffDetail = diffDetail;
    }
}
