package com.lp.course.upload;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.config.CourseUploadProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Signs presigned credentials so a teacher uploads video bytes straight to object storage and
 * previews a stored video without learning its object key.
 *
 * <p>The client stays {@code null} when credentials are absent, so a misconfigured deployment fails
 * with {@code 50206} instead of handing out a URL that cannot work.
 *
 * @author Caspian
 * @since 0.7.0
 */
@Component
public class LessonUploadUrlSigner {

    private final CourseUploadProperties properties;

    private final MinioClient minioClient;

    /**
     * Creates the signer and builds the storage client once.
     *
     * @param properties endpoint, credentials and bucket
     * @author Caspian
     * @since 0.7.0
     */
    public LessonUploadUrlSigner(CourseUploadProperties properties) {
        this.properties = properties;
        String accessKey = properties.minioAccessKey();
        String secretKey = properties.minioSecretKey();
        this.minioClient =
                accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()
                        ? null
                        : MinioClient.builder()
                                .endpoint(properties.minioEndpoint())
                                .region(properties.minioRegion())
                                .credentials(accessKey, secretKey)
                                .build();
    }

    /**
     * Signs one PUT credential for a lesson video object.
     *
     * @param objectKey server generated key, never taken from the original file name
     * @param ttl how long the URL stays valid, at least one second
     * @return absolute presigned PUT URL
     * @throws BizException with {@code 50206} when credentials are missing or signing fails
     * @author Caspian
     * @since 0.7.0
     */
    public String presignPut(String objectKey, Duration ttl) {
        return presign(Method.PUT, objectKey, ttl);
    }

    /**
     * Signs one GET credential so a teacher can preview a stored lesson video.
     *
     * @param objectKey stored object key, never taken from a request
     * @param ttl how long the URL stays valid, at least one second
     * @return absolute presigned GET URL
     * @throws BizException with {@code 50206} when credentials are missing or signing fails
     * @author Caspian
     * @since 0.10.0
     */
    public String presignGet(String objectKey, Duration ttl) {
        return presign(Method.GET, objectKey, ttl);
    }

    private String presign(Method method, String objectKey, Duration ttl) {
        if (minioClient == null) {
            throw new BizException(ErrorCode.LEARN_STORAGE_ERROR);
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(method)
                            .bucket(properties.minioBucket())
                            .object(objectKey)
                            .expiry((int) Math.max(ttl.toSeconds(), 1), TimeUnit.SECONDS)
                            .build());
        } catch (Exception exception) {
            throw new BizException(ErrorCode.LEARN_STORAGE_ERROR);
        }
    }
}
