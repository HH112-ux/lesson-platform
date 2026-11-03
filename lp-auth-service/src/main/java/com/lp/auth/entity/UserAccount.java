package com.lp.auth.entity;

/**
 * Account row used by the login query.
 *
 * <p>Only the columns needed to authenticate a user are selected, so no password hash ever reaches
 * the controller layer or a response payload.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class UserAccount {

    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String email;

    private String phone;

    private Integer status;

    /**
     * Returns the user id.
     *
     * @return primary key
     * @author Caspian
     * @since 0.1.0
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the user id.
     *
     * @param id primary key
     * @author Caspian
     * @since 0.1.0
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the login name.
     *
     * @return username
     * @author Caspian
     * @since 0.1.0
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the login name.
     *
     * @param username login name
     * @author Caspian
     * @since 0.1.0
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the BCrypt hash.
     *
     * @return password hash, never written to a response
     * @author Caspian
     * @since 0.1.0
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets the BCrypt hash.
     *
     * @param passwordHash BCrypt hash
     * @author Caspian
     * @since 0.1.0
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Returns the display name.
     *
     * @return nickname
     * @author Caspian
     * @since 0.1.0
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Sets the display name.
     *
     * @param nickname display name
     * @author Caspian
     * @since 0.1.0
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * Returns the avatar address.
     *
     * @return avatar URL, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /**
     * Sets the avatar address.
     *
     * @param avatarUrl avatar URL
     * @author Caspian
     * @since 0.1.0
     */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    /**
     * Returns the email address.
     *
     * @return email, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email address.
     *
     * @param email email address
     * @author Caspian
     * @since 0.1.0
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the phone number.
     *
     * @return phone, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone number.
     *
     * @param phone phone number
     * @author Caspian
     * @since 0.1.0
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns the account status.
     *
     * @return 1 active, 0 disabled, 2 cooling off, 3 closed
     * @author Caspian
     * @since 0.1.0
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the account status.
     *
     * @param status account status
     * @author Caspian
     * @since 0.1.0
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
}
