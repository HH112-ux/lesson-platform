package com.lp.trade.dto;

/**
 * Response body of {@code POST /api/v1/orders/purchase-ticket/redeem}.
 *
 * <p>The browser replaces its stored token pair with these values, then loads the course and
 * creates the order exactly like a normal web buyer. {@code courseId} saves a lookup round trip.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class RedeemTicketView {

    private final String accessToken;

    private final String refreshToken;

    private final long expiresIn;

    private final long courseId;

    /**
     * Creates the response.
     *
     * @param accessToken signed access token
     * @param refreshToken opaque refresh token
     * @param expiresIn access token lifetime in seconds
     * @param courseId course the ticket was issued for
     * @author Caspian
     * @since 0.1.0
     */
    public RedeemTicketView(
            String accessToken, String refreshToken, long expiresIn, long courseId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.courseId = courseId;
    }

    /**
     * Returns the access token.
     *
     * @return signed access token
     * @author Caspian
     * @since 0.1.0
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Returns the refresh token.
     *
     * @return opaque refresh token
     * @author Caspian
     * @since 0.1.0
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Returns the access token lifetime in seconds.
     *
     * @return lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Returns the course the ticket was issued for.
     *
     * @return course id
     * @author Caspian
     * @since 0.1.0
     */
    public long getCourseId() {
        return courseId;
    }
}
