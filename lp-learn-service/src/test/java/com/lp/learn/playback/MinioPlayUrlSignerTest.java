package com.lp.learn.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.config.LearnProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the MinIO signer configuration guard.
 *
 * <p>No network call is made: a deployment without credentials must fail before the SDK is built.
 *
 * @author Caspian
 * @since 0.1.0
 */
class MinioPlayUrlSignerTest {

    /**
     * Blank credentials fail explicitly with {@code 50206}; no placeholder URL is returned.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void missingCredentialsFailsExplicitly() {
        LearnProperties properties = new LearnProperties();
        properties.setMinioAccessKey("");
        properties.setMinioSecretKey("");
        MinioPlayUrlSigner signer = new MinioPlayUrlSigner(properties);

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> signer.presign("media/lesson-5.mp4", Duration.ofMinutes(10)));

        assertEquals(ErrorCode.LEARN_STORAGE_ERROR.getCode(), exception.getCode());
    }
}
