package com.lp.course.upload;

import com.lp.course.config.LessonTranscodeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Transcodes one lesson video to a standardized MP4 with a locked down {@code ffmpeg} call.
 *
 * <p>The output is H.264 ({@code libx264}, {@code medium}, CRF 23) with AAC 96 kbps audio and a
 * front loaded index ({@code +faststart}); the longest side is capped at 1280 pixels with the
 * original aspect ratio preserved and even output dimensions. Arguments are passed as an array
 * without a shell, only the {@code file} protocol is allowed, and the environment is limited to
 * {@code PATH} and {@code LANG}.
 *
 * <p>One run is bounded by {@code lp.course.transcode.timeout}. On timeout descendants are
 * destroyed first, then the process gets {@code SIGTERM} and finally {@code SIGKILL}.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Component
public class LessonVideoTranscoder {

    /** Longest side of the transcoded output in pixels. */
    static final int MAX_LONG_SIDE = 1280;

    /** Cap on captured stderr so a noisy failure cannot exhaust heap. */
    static final int MAX_STDERR_BYTES = 8 * 1024;

    /** Scale expression preserving the aspect ratio and capping the longest side at 720p. */
    static final String SCALE_FILTER =
            "scale='if(gte(iw,ih),min("
                    + MAX_LONG_SIDE
                    + ",iw),-2)':'if(gte(iw,ih),-2,min("
                    + MAX_LONG_SIDE
                    + ",ih))'";

    private static final String DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin";

    private final String ffmpegPath;

    private final Duration timeout;

    /**
     * Creates the transcoder with its binary path and time limit.
     *
     * @param properties transcode settings
     * @author Caspian
     * @since 0.11.0
     */
    public LessonVideoTranscoder(LessonTranscodeProperties properties) {
        this.ffmpegPath = properties.ffmpegPath();
        this.timeout = properties.timeout();
    }

    /**
     * Transcodes one local file into a new local MP4.
     *
     * @param source downloaded source video
     * @param output path of the file to create; the caller owns its directory
     * @throws IllegalStateException when the process cannot start, times out, exits non zero, or
     *     produces no bytes
     * @author Caspian
     * @since 0.11.0
     */
    public void transcode(Path source, Path output) {
        Process process = null;
        try {
            process = start(source, output);
            Process started = process;
            CompletableFuture<String> errors =
                    CompletableFuture.supplyAsync(() -> readErrors(started));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("ffmpeg timed out after " + timeout);
            }
            String stderr = errors.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "ffmpeg exited with " + process.exitValue() + lastLine(stderr));
            }
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                throw new IllegalStateException("ffmpeg produced no output" + lastLine(stderr));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("transcode interrupted");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("ffmpeg failed: " + exception.getMessage());
        } finally {
            if (process != null) {
                kill(process);
            }
        }
    }

    /**
     * Builds the fixed transcoding command line.
     *
     * @param ffmpegPath binary path or executable name
     * @param source source file
     * @param output output file
     * @return arguments in execution order
     * @author Caspian
     * @since 0.11.0
     */
    static List<String> command(String ffmpegPath, Path source, Path output) {
        return List.of(
                ffmpegPath,
                "-hide_banner",
                "-nostdin",
                "-y",
                "-loglevel",
                "error",
                "-protocol_whitelist",
                "file",
                "-f",
                "mp4",
                "-i",
                source.toString(),
                "-map",
                "0:v:0",
                "-map",
                "0:a:0?",
                "-vf",
                SCALE_FILTER,
                "-c:v",
                "libx264",
                "-preset",
                "medium",
                "-crf",
                "23",
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                "aac",
                "-b:a",
                "96k",
                "-movflags",
                "+faststart",
                "-f",
                "mp4",
                output.toString());
    }

    private Process start(Path source, Path output) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command(ffmpegPath, source, output));
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("PATH", environmentPath());
        environment.put("LANG", "C");
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    private static String readErrors(Process process) {
        try (InputStream input = process.getErrorStream()) {
            byte[] bytes = input.readNBytes(MAX_STDERR_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private static String lastLine(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }
        String[] lines = stderr.strip().split("\\R");
        String last = lines[lines.length - 1].strip();
        return last.isEmpty() ? "" : ": " + last;
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

    private static String environmentPath() {
        String path = System.getenv("PATH");
        return path == null || path.isBlank() ? DEFAULT_PATH : path;
    }
}
