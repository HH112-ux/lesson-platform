package com.lp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Register request body of {@code POST /api/v1/auth/register}.
 *
 * <p>Only student self registration is accepted here, so the body never carries a role code: the
 * service always grants {@code STUDENT}. Email and phone are optional and blank values are dropped
 * before the insert so they cannot collide through an empty unique key.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "username must be 4 to 20 letters or digits")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 6, max = 64, message = "password must be between 6 and 64 characters")
    private String password;

    @NotBlank(message = "nickname is required")
    @Size(max = 32, message = "nickname must not exceed 32 characters")
    private String nickname;

    @Email(message = "email format is invalid")
    @Size(max = 128, message = "email must not exceed 128 characters")
    private String email;

    @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "phone format is invalid")
    private String phone;

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
     * Returns the raw password.
     *
     * @return password, never logged or echoed
     * @author Caspian
     * @since 0.1.0
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the raw password.
     *
     * @param password password
     * @author Caspian
     * @since 0.1.0
     */
    public void setPassword(String password) {
        this.password = password;
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
     * Returns the optional email address.
     *
     * @return email, may be {@code null} or blank
     * @author Caspian
     * @since 0.1.0
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the optional email address.
     *
     * @param email email address
     * @author Caspian
     * @since 0.1.0
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the optional phone number.
     *
     * @return phone, may be {@code null} or blank
     * @author Caspian
     * @since 0.1.0
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the optional phone number.
     *
     * @param phone phone number
     * @author Caspian
     * @since 0.1.0
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }
}
