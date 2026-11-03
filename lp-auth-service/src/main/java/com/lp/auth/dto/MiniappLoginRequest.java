package com.lp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login request body of {@code POST /api/v1/auth/miniapp-login}.
 *
 * <p>Only the one-time {@code code} from {@code wx.login} is accepted. The client never sends
 * {@code openid}, {@code unionid} or role codes: the server resolves identity from WeChat.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class MiniappLoginRequest {

    @NotBlank(message = "code is required")
    @Size(max = 64, message = "code must not exceed 64 characters")
    private String code;

    /**
     * Returns the one-time login code.
     *
     * @return code from {@code wx.login}
     * @author Caspian
     * @since 0.1.0
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the one-time login code.
     *
     * @param code code from {@code wx.login}
     * @author Caspian
     * @since 0.1.0
     */
    public void setCode(String code) {
        this.code = code;
    }
}
