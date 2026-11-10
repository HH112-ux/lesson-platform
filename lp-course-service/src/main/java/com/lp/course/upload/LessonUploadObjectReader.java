package com.lp.course.upload;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.course.config.CourseUploadProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

/**
 * Reads uploaded lesson videos back from object storage.
 *
 * <p>A download lands in a private temporary directory owned by the caller, who must delete it via
 * {@link #delete}. Each call is bounded by a fifteen second timeout, so a stat plus a download stay
 * inside the thirty second confirmation budget.
 *
 * @author Caspian
 * @since 0.8.0
 */
@Component
public class LessonUploadObjectReader {

    private static final int CALL_TIMEOUT_SECONDS = 15;

    private final CourseUploadProperties properties;

    private final MinioClient minioClient;

    /**
     * Creates the reader and builds its client once.
     *
     * @param properties endpoint, credentials and bucket
     * @author Caspian
     * @since 0.8.0
     */
    public LessonUploadObjectReader(CourseUploadProperties properties) {
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
                                .httpClient(
                                        new OkHttpClient.Builder()
                                                .readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                                .build())
                                .build();
    }

    /**
     * Reads the stored object size.
     *
     * @param objectKey server generated object key
     * @return size in bytes
     * @throws BizException {@code 50206} when unconfigured, {@code 61003} when the object is absent
     * @author Caspian
     * @since 0.8.0
     */
    public long statSize(String objectKey) {
        if (minioClient == null) {
            throw new BizException(ErrorCode.LEARN_STORAGE_ERROR);
        }
        try {
            return minioClient
                    .statObject(
                            StatObjectArgs.builder()
                                    .bucket(properties.minioBucket())
                                    .object(objectKey)
                                    .build())
                    .size();
        } catch (Exception exception) {
            throw new BizException(
                    ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "uploaded object is missing");
        }
    }

    /**
     * Downloads one object into a private temporary directory while hashing the bytes written.
     *
     * @param objectKey server generated object key
     * @return the file and its SHA-256; the caller must pass it to {@link #delete}
     * @throws BizException {@code 50206} when unconfigured, {@code 61003} when the object cannot be
     *     read
     * @author Caspian
     * @since 0.8.0
     */
    public DownloadedVideo download(String objectKey) {
        if (minioClient == null) {
            throw new BizException(ErrorCode.LEARN_STORAGE_ERROR);
        }
        Path directory = null;
        try {
            directory =
                    Files.createTempDirectory(
                            "lp-lesson-video-",
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rwx------")));
            Path file =
                    Files.createFile(
                            directory.resolve("video.mp4"),
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rw-------")));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input =
                            minioClient.getObject(
                                    GetObjectArgs.builder()
                                            .bucket(properties.minioBucket())
                                            .object(objectKey)
                                            .build());
                    OutputStream output =
                            new DigestOutputStream(Files.newOutputStream(file), digest)) {
                input.transferTo(output);
            }
            return new DownloadedVideo(directory, file, HexFormat.of().formatHex(digest.digest()));
        } catch (Exception exception) {
            deleteQuietly(directory);
            throw new BizException(
                    ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "uploaded object cannot be read");
        }
    }

    /**
     * Removes the downloaded file and its directory, also on the failure path.
     *
     * @param video download returned by {@link #download}
     * @author Caspian
     * @since 0.8.0
     */
    public void delete(DownloadedVideo video) {
        deleteQuietly(video.directory());
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                    // A leftover under the OS temp root never reaches the caller.
                                }
                            });
        } catch (IOException ignored) {
            // Cleanup must not replace the confirmation result.
        }
    }

    /**
     * One downloaded video held in a private temporary directory.
     *
     * @param directory random temporary directory owned by this download
     * @param file downloaded {@code mp4} file with owner only permissions
     * @param sha256 lower case hex digest of the bytes written
     * @author Caspian
     * @since 0.8.0
     */
    public record DownloadedVideo(Path directory, Path file, String sha256) {}
}
