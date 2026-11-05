package com.lp.learn.playback;

import java.time.Duration;

/**
 * Produces time limited playback URLs.
 *
 * <p>Abstracted so the play-token flow can be tested without MinIO. The implementation must fail
 * loudly when credentials are missing; returning a fabricated URL would leak nothing but would
 * break playback silently.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface PlayUrlSigner {

    /**
     * Signs one media object for browser playback.
     *
     * @param objectKey MinIO object key
     * @param ttl how long the URL stays valid
     * @return absolute presigned URL
     * @throws com.lp.common.exception.BizException with {@code 50206} when storage is not
     *     configured or signing fails
     * @author Caspian
     * @since 0.1.0
     */
    String presign(String objectKey, Duration ttl);
}
