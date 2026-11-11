package com.lp.course.upload;

import com.lp.course.config.CourseUploadProperties;
import com.lp.course.config.LessonTranscodeProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

/**
 * Reads source videos and writes transcoded copies in the lesson media bucket.
 *
 * <p>The confirmation path keeps its fifteen second budget; transcoding needs minutes for large
 * files, so this component builds its own client with the configured download and upload limits.
 * Outputs go under the {@code transcoded/} prefix and never replace the source object.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Component
public class LessonTranscodeObjectStore {

    private static final String OUTPUT_PREFIX = "transcoded/";

    private static final String OUTPUT_CONTENT_TYPE = "video/mp4";

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    private final CourseUploadProperties upload;

    private final MinioClient minioClient;

    /**
     * Creates the store and its long timeout client once.
     *
     * @param upload endpoint, credentials and bucket
     * @param transcode download and upload limits
     * @author Caspian
     * @since 0.11.0
     */
    public LessonTranscodeObjectStore(
            CourseUploadProperties upload, LessonTranscodeProperties transcode) {
        this.upload = upload;
        String accessKey = upload.minioAccessKey();
        String secretKey = upload.minioSecretKey();
        Duration callTimeout = longer(transcode.downloadTimeout(), transcode.uploadTimeout());
        this.minioClient =
                accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()
                        ? null
                        : MinioClient.builder()
                                .endpoint(upload.minioEndpoint())
                                .region(upload.minioRegion())
                                .credentials(accessKey, secretKey)
                                .httpClient(
                                        new OkHttpClient.Builder()
                                                .connectTimeout(
                                                        CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                                .readTimeout(
                                                        callTimeout.toMillis(),
                                                        TimeUnit.MILLISECONDS)
                                                .writeTimeout(
                                                        callTimeout.toMillis(),
                                                        TimeUnit.MILLISECONDS)
                                                .callTimeout(
                                                        callTimeout.toMillis(),
                                                        TimeUnit.MILLISECONDS)
                                                .build())
                                .build();
    }

    /**
     * Derives the output key of one source object.
     *
     * @param sourceObjectKey stored source object key such as {@code lesson-videos/77/501.mp4}
     * @return {@code transcoded/} followed by the source key, so lesson and upload ids stay visible
     * @author Caspian
     * @since 0.11.0
     */
    public String outputKey(String sourceObjectKey) {
        return OUTPUT_PREFIX + sourceObjectKey;
    }

    /**
     * Downloads one object into a private temporary directory.
     *
     * @param sourceObjectKey stored source object key
     * @return downloaded file; the caller must pass its directory to {@link #delete}
     * @throws IllegalStateException when storage is unconfigured or the object cannot be read
     * @author Caspian
     * @since 0.11.0
     */
    public Path download(String sourceObjectKey) {
        if (minioClient == null) {
            throw new IllegalStateException("object storage is not configured");
        }
        Path directory = null;
        try {
            directory =
                    Files.createTempDirectory(
                            "lp-transcode-",
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rwx------")));
            Path file =
                    Files.createFile(
                            directory.resolve("source.mp4"),
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions.fromString("rw-------")));
            try (InputStream input =
                            minioClient.getObject(
                                    GetObjectArgs.builder()
                                            .bucket(upload.minioBucket())
                                            .object(sourceObjectKey)
                                            .build());
                    OutputStream output = Files.newOutputStream(file)) {
                input.transferTo(output);
            }
            return file;
        } catch (Exception exception) {
            deleteQuietly(directory);
            throw new IllegalStateException(
                    "transcode source download failed: " + exception.getMessage());
        }
    }

    /**
     * Uploads a transcoded file without overwriting the source object.
     *
     * @param outputObjectKey key derived by {@link #outputKey}
     * @param file local transcoded MP4
     * @throws IllegalStateException when storage is unconfigured or the upload fails
     * @author Caspian
     * @since 0.11.0
     */
    public void upload(String outputObjectKey, Path file) {
        if (minioClient == null) {
            throw new IllegalStateException("object storage is not configured");
        }
        try (InputStream input = Files.newInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(upload.minioBucket())
                            .object(outputObjectKey)
                            .stream(input, Files.size(file), -1)
                            .contentType(OUTPUT_CONTENT_TYPE)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "transcode output upload failed: " + exception.getMessage());
        }
    }

    /**
     * Removes a downloaded directory and every file below it.
     *
     * @param directory directory returned by {@link #download}, may be {@code null}
     * @author Caspian
     * @since 0.11.0
     */
    public void delete(Path directory) {
        deleteQuietly(directory);
    }

    private static Duration longer(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
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
            // Cleanup must not replace the transcode result.
        }
    }
}
