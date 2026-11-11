package com.lp.course.cleanup;

import com.lp.course.config.CourseUploadProperties;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deletes one source object from the lesson video bucket.
 *
 * <p>A deletion that finds nothing to delete is a success: another actor already removed the
 * object, or a previous tick deleted it and failed to record that. Only errors that will not heal
 * on their own are reported as permanent.
 *
 * @author Caspian
 * @since 0.17.0
 */
@Component
public class LessonCleanupObjectStore {

    private static final Logger log = LoggerFactory.getLogger(LessonCleanupObjectStore.class);

    /** Storage errors that a retry cannot fix. */
    private static final Set<String> PERMANENT_CODES =
            Set.of(
                    "AccessDenied",
                    "Forbidden",
                    "InvalidAccessKeyId",
                    "InvalidArgument",
                    "InvalidBucketName",
                    "NoSuchBucket",
                    "SignatureDoesNotMatch");

    private final MinioClient client;
    private final CourseUploadProperties upload;

    /**
     * Builds the client from the shared upload settings.
     *
     * @param upload storage settings of the course service
     * @author Caspian
     * @since 0.17.0
     */
    public LessonCleanupObjectStore(CourseUploadProperties upload) {
        this.upload = upload;
        String accessKey = upload.minioAccessKey();
        String secretKey = upload.minioSecretKey();
        this.client =
                accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()
                        ? null
                        : MinioClient.builder()
                                .endpoint(upload.minioEndpoint())
                                .region(upload.minioRegion())
                                .credentials(accessKey, secretKey)
                                .build();
    }

    /**
     * Deletes one object; a missing object counts as deleted.
     *
     * @param objectKey object key to remove
     * @throws CleanupFailure when the object could not be removed
     * @author Caspian
     * @since 0.17.0
     */
    public void delete(String objectKey) {
        if (client == null) {
            throw new CleanupFailure(false, "storage is not configured");
        }
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(upload.minioBucket())
                            .object(objectKey)
                            .build());
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null ? null : e.errorResponse().code();
            int httpStatus = e.response() == null ? 0 : e.response().code();
            if (isMissing(code, httpStatus)) {
                log.debug("cleanup found {} already removed", objectKey);
                return;
            }
            throw new CleanupFailure(PERMANENT_CODES.contains(code), String.valueOf(code));
        } catch (IllegalArgumentException e) {
            throw new CleanupFailure(true, "invalid object key");
        } catch (Exception e) {
            throw new CleanupFailure(false, e.getClass().getSimpleName());
        }
    }

    /**
     * Tells whether an error response means the object is already gone.
     *
     * @param code storage error code, may be {@code null}
     * @param httpStatus HTTP status of the response
     * @return {@code true} when nothing is left to delete
     * @author Caspian
     * @since 0.17.0
     */
    static boolean isMissing(String code, int httpStatus) {
        return httpStatus == 404
                || "NoSuchKey".equalsIgnoreCase(code)
                || "NotFound".equalsIgnoreCase(code);
    }

    /** One failed deletion, flagged as retryable or terminal. */
    static final class CleanupFailure extends RuntimeException {

        private final boolean permanent;

        CleanupFailure(boolean permanent, String message) {
            super(message);
            this.permanent = permanent;
        }

        boolean permanent() {
            return permanent;
        }
    }
}
