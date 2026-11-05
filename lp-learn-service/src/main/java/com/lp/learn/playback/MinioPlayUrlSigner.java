package com.lp.learn.playback;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.config.LearnProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * MinIO backed {@link PlayUrlSigner}.
 *
 * <p>The client is created lazily once credentials exist. When the access or secret key is blank
 * the signer throws {@code 50206} instead of returning a placeholder URL, so a misconfigured
 * deployment fails visibly rather than breaking playback in the browser.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Component
public class MinioPlayUrlSigner implements PlayUrlSigner {

    private final LearnProperties learnProperties;

    private volatile MinioClient minioClient;

    /**
     * Creates the signer from learn configuration.
     *
     * @param learnProperties MinIO endpoint, credentials, bucket and TTL
     * @author Caspian
     * @since 0.1.0
     */
    public MinioPlayUrlSigner(LearnProperties learnProperties) {
        this.learnProperties = learnProperties;
    }

    /**
     * Signs one media object for browser playback.
     *
     * @param objectKey MinIO object key
     * @param ttl how long the URL stays valid
     * @return absolute presigned URL
     * @throws BizException with {@code 50206} when credentials are missing or signing fails
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public String presign(String objectKey, Duration ttl) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "lesson has no video");
        }
        MinioClient client = client();
        try {
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(learnProperties.getMinioBucket())
                            .object(objectKey)
                            .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception exception) {
            throw new BizException(ErrorCode.LEARN_STORAGE_ERROR);
        }
    }

    private MinioClient client() {
        String accessKey = learnProperties.getMinioAccessKey();
        String secretKey = learnProperties.getMinioSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new BizException(ErrorCode.LEARN_STORAGE_ERROR);
        }
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient =
                            MinioClient.builder()
                                    .endpoint(learnProperties.getMinioEndpoint())
                                    .credentials(accessKey, secretKey)
                                    .build();
                }
            }
        }
        return minioClient;
    }
}
