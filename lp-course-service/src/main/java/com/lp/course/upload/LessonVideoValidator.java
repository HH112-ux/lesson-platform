package com.lp.course.upload;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies the frozen lesson video acceptance rules to probed metadata: accepted MP4 brand, H.264,
 * AAC or no audio, at most 1080p with even dimensions, duration from five seconds to four hours.
 * Frame rate, bitrate, pixel format, minimum resolution and {@code moov} position are not checked
 * because the product has not frozen those limits.
 *
 * @author Caspian
 * @since 0.7.0
 */
@Component
public class LessonVideoValidator {

    /** Accepted {@code ftyp} major brands; {@code M4V } is four bytes including its space. */
    static final Set<String> ACCEPTED_BRANDS = Set.of("isom", "mp42", "avc1", "mp41", "M4V ");

    static final int MAX_WIDTH = 1920;

    static final int MAX_HEIGHT = 1080;

    static final double MIN_DURATION_SECONDS = 5;

    static final double MAX_DURATION_SECONDS = 4 * 60 * 60;

    /**
     * Rejects a video that does not satisfy the acceptance rules.
     *
     * @param video probed metadata
     * @throws BizException with {@code 61003} for the first rule that fails
     * @author Caspian
     * @since 0.7.0
     */
    public void validate(LessonVideoMetadata video) {
        if (!ACCEPTED_BRANDS.contains(video.majorBrand())) {
            throw reject("only mp4 is accepted");
        }
        if (!"h264".equals(video.videoCodec())) {
            throw reject("video codec must be h264");
        }
        if (video.audioCodec() != null && !"aac".equals(video.audioCodec())) {
            throw reject("audio codec must be aac");
        }
        requireResolution(video);
        requireDuration(video.durationSeconds());
    }

    private void requireResolution(LessonVideoMetadata video) {
        boolean tooLarge = video.width() > MAX_WIDTH || video.height() > MAX_HEIGHT;
        boolean odd = video.width() % 2 != 0 || video.height() % 2 != 0;
        if (video.width() <= 0 || video.height() <= 0 || tooLarge || odd) {
            throw reject("resolution must be even and at most 1080p");
        }
    }

    /** Compares with a negated range so that a NaN duration is rejected as well. */
    private void requireDuration(double seconds) {
        if (!(seconds >= MIN_DURATION_SECONDS && seconds <= MAX_DURATION_SECONDS)) {
            throw reject("duration must be between 5 seconds and 4 hours");
        }
    }

    private BizException reject(String reason) {
        return new BizException(ErrorCode.LESSON_UPLOAD_VIDEO_INVALID, reason);
    }
}
