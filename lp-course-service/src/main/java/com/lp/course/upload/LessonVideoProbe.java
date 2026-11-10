package com.lp.course.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads lesson video metadata with a locked down {@code ffprobe} call: no transcoding or copying,
 * arguments passed as an array without a shell, {@code file} as the only protocol, {@code mp4} as
 * the pinned format, capped probe size and analysis duration, and an environment limited to {@code
 * PATH} and {@code LANG}.
 *
 * <p>One probe is limited to five seconds; on timeout descendants are destroyed first, then the
 * process gets {@code SIGTERM} and {@code SIGKILL}.
 *
 * @author Caspian
 * @since 0.7.0
 */
@Component
public class LessonVideoProbe {

    static final long TIMEOUT_SECONDS = 5;
    static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final int FTYP_HEADER_BYTES = 12;
    private static final String DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String ffprobePath;

    /**
     * Creates the probe with the binary path taken from configuration.
     *
     * @param ffprobePath absolute path or executable name of {@code ffprobe}
     * @author Caspian
     * @since 0.7.0
     */
    public LessonVideoProbe(@Value("${lp.course.upload.ffprobe-path:ffprobe}") String ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    /**
     * Probes one local video file.
     *
     * @param file downloaded video file
     * @return container brand, duration and first video and audio stream facts
     * @throws BizException with {@code 61003} when the file is unreadable, not MP4, too slow or
     *     unparsable
     * @author Caspian
     * @since 0.7.0
     */
    public LessonVideoMetadata probe(Path file) {
        String brand = readMajorBrand(file);
        if (brand == null) {
            throw new BizException(
                    ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "mp4 ftyp box is missing");
        }
        Process process = null;
        try {
            process = start(file);
            Process started = process;
            CompletableFuture<String> output =
                    CompletableFuture.supplyAsync(() -> readOutput(started));
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new BizException(ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "probe timed out");
            }
            String json = output.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || json.isBlank()) {
                throw new BizException(
                        ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "video cannot be parsed");
            }
            return parse(brand, json);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "video cannot be parsed");
        } finally {
            if (process != null) {
                kill(process);
            }
        }
    }

    static String readMajorBrand(Path file) {
        byte[] header = new byte[FTYP_HEADER_BYTES];
        try (InputStream input = Files.newInputStream(file)) {
            if (input.readNBytes(header, 0, FTYP_HEADER_BYTES) < FTYP_HEADER_BYTES) {
                return null;
            }
        } catch (IOException exception) {
            return null;
        }
        if (header[4] != 'f' || header[5] != 't' || header[6] != 'y' || header[7] != 'p') {
            return null;
        }
        return new String(header, 8, 4, StandardCharsets.ISO_8859_1);
    }

    private Process start(Path file) throws IOException {
        ProcessBuilder builder =
                new ProcessBuilder(
                        List.of(
                                ffprobePath,
                                "-v",
                                "error",
                                "-protocol_whitelist",
                                "file",
                                "-f",
                                "mp4",
                                "-probesize",
                                "5000000",
                                "-analyzeduration",
                                "3000000",
                                "-print_format",
                                "json",
                                "-show_format",
                                "-show_streams",
                                file.toString()));
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("PATH", environmentPath());
        environment.put("LANG", "C");
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    private static String readOutput(Process process) {
        try (InputStream input = process.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_OUTPUT_BYTES + 1);
            if (bytes.length > MAX_OUTPUT_BYTES) {
                throw new BizException(
                        ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "probe output is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BizException(ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, "video cannot be parsed");
        }
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static LessonVideoMetadata parse(String brand, String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        String videoCodec = null;
        String audioCodec = null;
        int width = 0;
        int height = 0;
        for (JsonNode stream : root.path("streams")) {
            String type = stream.path("codec_type").asText("");
            String codec = stream.path("codec_name").asText(null);
            if ("video".equals(type) && videoCodec == null) {
                videoCodec = codec;
                width = stream.path("width").asInt(0);
                height = stream.path("height").asInt(0);
            } else if ("audio".equals(type)) {
                // Any non AAC track must surface, so a mixed audio layout is still rejected.
                audioCodec = audioCodec == null || "aac".equals(audioCodec) ? codec : audioCodec;
            }
        }
        double duration;
        try {
            duration = Double.parseDouble(root.path("format").path("duration").asText(""));
        } catch (NumberFormatException exception) {
            duration = 0;
        }
        return new LessonVideoMetadata(brand, duration, videoCodec, width, height, audioCodec);
    }

    private static String environmentPath() {
        String path = System.getenv("PATH");
        return path == null || path.isBlank() ? DEFAULT_PATH : path;
    }
}
