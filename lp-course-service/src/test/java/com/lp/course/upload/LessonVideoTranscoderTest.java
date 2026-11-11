package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lp.course.config.LessonTranscodeProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the locked down {@code ffmpeg} invocation.
 *
 * @author Caspian
 * @since 0.11.0
 */
class LessonVideoTranscoderTest {

    private static final Path SOURCE = Path.of("/tmp/source.mp4");

    private static final Path OUTPUT = Path.of("/tmp/output.mp4");

    @Test
    void commandPinsStandardizedEncodingParameters() {
        List<String> command = LessonVideoTranscoder.command("ffmpeg", SOURCE, OUTPUT);

        assertTrue(command.containsAll(List.of("-c:v", "libx264")));
        assertEquals("medium", valueAfter(command, "-preset"));
        assertEquals("23", valueAfter(command, "-crf"));
        assertEquals("96k", valueAfter(command, "-b:a"));
        assertEquals("aac", valueAfter(command, "-c:a"));
        assertEquals("+faststart", valueAfter(command, "-movflags"));
        assertEquals(SOURCE.toString(), valueAfter(command, "-i"));
        assertEquals(OUTPUT.toString(), command.get(command.size() - 1));
    }

    @Test
    void scaleKeepsAspectRatioAndCapsTheLongestSide() {
        String filter = LessonVideoTranscoder.SCALE_FILTER;

        assertTrue(filter.contains("min(1280,iw)"));
        assertTrue(filter.contains("min(1280,ih)"));
        assertTrue(filter.contains("-2"));
    }

    @Test
    void missingBinaryFailsLoudly() {
        LessonTranscodeProperties properties = properties("ffmpeg-does-not-exist");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> new LessonVideoTranscoder(properties).transcode(SOURCE, OUTPUT));

        assertTrue(exception.getMessage().startsWith("ffmpeg failed"));
    }

    private static String valueAfter(List<String> command, String key) {
        int index = command.indexOf(key);
        assertTrue(index >= 0 && index + 1 < command.size(), key + " is missing");
        return command.get(index + 1);
    }

    private static LessonTranscodeProperties properties(String ffmpegPath) {
        return new LessonTranscodeProperties(
                true,
                1,
                4,
                ffmpegPath,
                Duration.ofSeconds(5),
                3,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1));
    }
}
