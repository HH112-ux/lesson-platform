package com.lp.trade.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row read from {@code coupon}.
 *
 * <p>Type 1 is a fixed amount reduction and type 2 is a discount rate such as 0.85. Only enabled
 * coupons inside their validity window can be consumed by an order.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class Coupon {

    private Long id;

    private String code;

    private String name;

    private Integer type;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private BigDecimal discountRate;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Integer status;

    /**
     * Returns the primary key.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the primary key.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the redemption code.
     *
     * @return code
     * @author Caspian
     * @since 0.1.0
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the redemption code.
     *
     * @param code code
     * @author Caspian
     * @since 0.1.0
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Returns the display name.
     *
     * @return name
     * @author Caspian
     * @since 0.1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name.
     *
     * @param name name
     * @author Caspian
     * @since 0.1.0
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the coupon type.
     *
     * @return 1 fixed reduction, 2 discount rate
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getType() {
        return type;
    }

    /**
     * Sets the coupon type.
     *
     * @param type 1 fixed reduction, 2 discount rate
     * @author Caspian
     * @since 0.1.0
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * Returns the minimum order amount.
     *
     * @return threshold in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    /**
     * Sets the minimum order amount.
     *
     * @param thresholdAmount threshold in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    /**
     * Returns the fixed reduction amount.
     *
     * @return amount in yuan, may be {@code null} for a rate coupon
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    /**
     * Sets the fixed reduction amount.
     *
     * @param discountAmount amount in yuan
     * @author Caspian
     * @since 0.1.0
     */
    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    /**
     * Returns the discount rate.
     *
     * @return rate such as 0.85, may be {@code null} for a fixed reduction coupon
     * @author Caspian
     * @since 0.1.0
     */
    public BigDecimal getDiscountRate() {
        return discountRate;
    }

    /**
     * Sets the discount rate.
     *
     * @param discountRate rate such as 0.85
     * @author Caspian
     * @since 0.1.0
     */
    public void setDiscountRate(BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    /**
     * Returns the validity start.
     *
     * @return start time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getStartAt() {
        return startAt;
    }

    /**
     * Sets the validity start.
     *
     * @param startAt start time
     * @author Caspian
     * @since 0.1.0
     */
    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    /**
     * Returns the validity end.
     *
     * @return end time
     * @author Caspian
     * @since 0.1.0
     */
    public LocalDateTime getEndAt() {
        return endAt;
    }

    /**
     * Sets the validity end.
     *
     * @param endAt end time
     * @author Caspian
     * @since 0.1.0
     */
    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    /**
     * Returns the enabled flag.
     *
     * @return 1 enabled, 0 disabled
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the enabled flag.
     *
     * @param status 1 enabled, 0 disabled
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
}
