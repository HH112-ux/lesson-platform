package com.lp.course.upload;

/**
 * Container and stream facts read from one uploaded lesson video.
 *
 * <p>{@code majorBrand} comes from the {@code ftyp} box because {@code ffprobe} does not expose it.
 * {@code audioCodec} stays {@code null} for a silent video, which the rules accept. A duration of
 * {@code 0} means the container did not declare one.
 *
 * @author Caspian
 * @since 0.7.0
 */
public record LessonVideoMetadata(
        String majorBrand,
        double durationSeconds,
        String videoCodec,
        int width,
        int height,
        String audioCodec) {}
