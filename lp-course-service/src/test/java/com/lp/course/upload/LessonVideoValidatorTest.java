package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lp.common.exception.BizException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for the lesson video acceptance rules and the {@code ftyp} brand reader. */
class LessonVideoValidatorTest {
    private final LessonVideoValidator validator = new LessonVideoValidator();

    @Test
    void acceptsFullHdH264WithAacAndDurationBoundaries() {
        assertDoesNotThrow(() -> validator.validate(video("isom", 1920, 1080, 60, "aac")));
        assertDoesNotThrow(() -> validator.validate(video("isom", 1280, 720, 5, "aac")));
        assertDoesNotThrow(() -> validator.validate(video("isom", 1280, 720, 14400, "aac")));
    }

    @Test
    void acceptsSilentVideoAndBrandWithTrailingSpace() {
        assertDoesNotThrow(() -> validator.validate(video("M4V ", 640, 360, 5, null)));
    }

    @Test
    void rejectsFragmentOnlyBrand() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> validator.validate(video("dash", 1280, 720, 10, "aac")));
        assertEquals(61003, exception.getCode());
    }

    @Test
    void rejectsUnsupportedStreamCodecs() {
        assertThrows(
                BizException.class,
                () ->
                        validator.validate(
                                new LessonVideoMetadata("isom", 60, "hevc", 1280, 720, "aac")));
        assertThrows(
                BizException.class,
                () ->
                        validator.validate(
                                new LessonVideoMetadata("isom", 60, "h264", 1280, 720, "mp3")));
    }

    @Test
    void rejectsUnusableResolution() {
        assertThrows(
                BizException.class, () -> validator.validate(video("isom", 1281, 720, 60, "aac")));
        assertThrows(
                BizException.class, () -> validator.validate(video("isom", 1920, 1082, 60, "aac")));
        assertThrows(
                BizException.class, () -> validator.validate(video("isom", 3840, 2160, 60, "aac")));
    }

    @Test
    void rejectsDurationOutsideRange() {
        assertThrows(
                BizException.class, () -> validator.validate(video("isom", 1280, 720, 4.9, "aac")));
        assertThrows(
                BizException.class,
                () -> validator.validate(video("isom", 1280, 720, 14401, "aac")));
        assertThrows(
                BizException.class,
                () ->
                        validator.validate(
                                new LessonVideoMetadata(
                                        "isom", Double.NaN, "h264", 1280, 720, null)));
    }

    @Test
    void acceptsDurationBoundaries() {
        assertDoesNotThrow(() -> validator.validate(video("isom", 1280, 720, 5, "aac")));
        assertDoesNotThrow(() -> validator.validate(video("isom", 1280, 720, 14400, "aac")));
    }

    @Test
    void readsMajorBrandFromFtypBox(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("sample.mp4");
        byte[] header = new byte[12];
        header[3] = 24;
        System.arraycopy("ftypmp42".getBytes(StandardCharsets.ISO_8859_1), 0, header, 4, 8);
        Files.write(file, header);
        Path junk = directory.resolve("sample.bin");
        Files.write(junk, "not a video".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals("mp42", LessonVideoProbe.readMajorBrand(file));
        assertNull(LessonVideoProbe.readMajorBrand(junk));
    }

    private LessonVideoMetadata video(
            String brand, int width, int height, double duration, String audioCodec) {
        return new LessonVideoMetadata(brand, duration, "h264", width, height, audioCodec);
    }
}
