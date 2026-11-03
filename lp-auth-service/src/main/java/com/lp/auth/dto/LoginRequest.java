package com.lp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request body of {@code POST /api/v1/auth/login}.
 *
 * <p>Only account and password are accepted. The source platform is never taken from the
 * body: the gateway derives it from the entry segment.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class LoginRequest {

    @NotBlank(message = "account is required")
    @Size(max = 32, message = "account must not exceed 32 characters")
    private String account;

    @NotBlank(message = "password is required")
    @Size(min = 6, max = 64, message = "password must be between 6 and 64 characters")
    private String password;

    /**
     * Returns the login name.
     *
     * @return account
     * @author Caspian
     * @since 0.1.0
     */
    public String getAccount() {
        return account;
    }

    /**
     * Sets the login name.
     *
     * @param account login name
     * @author Caspian
     * @since 0.1.0
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * Returns the raw password.
     *
     * @return password
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
}
