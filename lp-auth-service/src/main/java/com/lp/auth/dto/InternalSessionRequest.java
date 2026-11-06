package com.lp.auth.dto;

/**
 * Request body of {@code POST /internal/v1/auth/session}.
 *
 * <p>Only trade-service calls this endpoint, and it must present {@code X-Internal-Token}. The user
 * id has already been proven by a one-time purchase ticket, so no password is involved.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class InternalSessionRequest {

    private Long userId;

    /**
     * Returns the account id to issue a session for.
     *
     * @return user id
     * @author Caspian
     * @since 0.1.0
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the account id to issue a session for.
     *
     * @param userId user id
     * @author Caspian
     * @since 0.1.0
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
