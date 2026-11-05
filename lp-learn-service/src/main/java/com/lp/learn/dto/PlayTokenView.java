package com.lp.learn.dto;

/**
 * Presigned playback URL handed to the client.
 *
 * <p>The returned URL is the only media handle a client ever sees; the object key stays behind.
 *
 * @author Caspian
 * @since 0.1.0
 */
public class PlayTokenView {

    private final String playUrl;

    private final long expiresIn;

    private final long expiresAt;

    /**
     * Creates one token.
     *
     * @param playUrl absolute presigned URL
     * @param expiresIn lifetime in seconds
     * @param expiresAt expiry as epoch seconds
     * @author Caspian
     * @since 0.1.0
     */
    public PlayTokenView(String playUrl, long expiresIn, long expiresAt) {
        this.playUrl = playUrl;
        this.expiresIn = expiresIn;
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the presigned URL.
     *
     * @return absolute URL
     * @author Caspian
     * @since 0.1.0
     */
    public String getPlayUrl() {
        return playUrl;
    }

    /**
     * Returns the URL lifetime.
     *
     * @return lifetime in seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Returns the absolute expiry.
     *
     * @return expiry as epoch seconds
     * @author Caspian
     * @since 0.1.0
     */
    public long getExpiresAt() {
        return expiresAt;
    }
}
