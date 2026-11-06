package com.lp.auth.entity;

import java.time.LocalDateTime;

/**
 * Account row projected for the operations console user list.
 *
 * <p>The password hash is never selected; only the fields the console renders are mapped.
 *
 * @author Caspian
 * @since 0.2.0
 */
public class AdminUserRow {

    private Long userId;

    private String username;

    private String nickname;

    private String phone;

    private Integer status;

    private LocalDateTime createdAt;

    /**
     * Returns the account id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.2.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the account id.
     *
     * @param userId primary key
     * @author Caspian
     * @since 0.2.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the login name.
     *
     * @return login name
     * @author Caspian
     * @since 0.2.0
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the login name.
     *
     * @param username login name
     * @author Caspian
     * @since 0.2.0
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the display name.
     *
     * @return nickname, may be {@code null}
     * @author Caspian
     * @since 0.2.0
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Sets the display name.
     *
     * @param nickname display name
     * @author Caspian
     * @since 0.2.0
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * Returns the bound phone number.
     *
     * @return phone, may be {@code null}
     * @author Caspian
     * @since 0.2.0
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the bound phone number.
     *
     * @param phone phone number
     * @author Caspian
     * @since 0.2.0
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns the account status.
     *
     * @return {@code 1} enabled, {@code 0} disabled
     * @author Caspian
     * @since 0.2.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the account status.
     *
     * @param status account status
     * @author Caspian
     * @since 0.2.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * Returns the registration time.
     *
     * @return creation time
     * @author Caspian
     * @since 0.2.0
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the registration time.
     *
     * @param createdAt creation time
     * @author Caspian
     * @since 0.2.0
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
