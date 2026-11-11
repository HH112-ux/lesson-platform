package com.lp.course.upload;

import com.lp.course.domain.LessonTranscodeStatus;
import com.lp.course.entity.LessonVideoTranscode;
import com.lp.course.mapper.LessonVideoTranscodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chooses the object key used for lesson playback.
 *
 * <p>This is the single decision point between the transcoded artifact and the confirmed original.
 * The artifact is used only when its task is READY and the output key is present; every other state
 * falls back to the original key, so a pending, running or failed transcode never blocks playback.
 * A lookup failure also falls back to the original, because the optimization must not break
 * playback.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Component
public class LessonPlaybackKeyResolver {

    private static final Logger LOG = LoggerFactory.getLogger(LessonPlaybackKeyResolver.class);

    private final LessonVideoTranscodeMapper mapper;

    /**
     * Creates the resolver.
     *
     * @param mapper task state access
     * @author Caspian
     * @since 0.11.0
     */
    public LessonPlaybackKeyResolver(LessonVideoTranscodeMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Returns the object key to sign for playback.
     *
     * @param sourceObjectKey confirmed source object key, may be {@code null}
     * @return transcoded key when the task is READY, otherwise the source key
     * @author Caspian
     * @since 0.11.0
     */
    public String resolve(String sourceObjectKey) {
        if (sourceObjectKey == null || sourceObjectKey.isBlank()) {
            return sourceObjectKey;
        }
        try {
            LessonVideoTranscode task = mapper.selectBySourceObjectKey(sourceObjectKey);
            if (task != null
                    && task.getStatus() != null
                    && task.getStatus() == LessonTranscodeStatus.READY
                    && task.getOutputObjectKey() != null
                    && !task.getOutputObjectKey().isBlank()) {
                return task.getOutputObjectKey();
            }
        } catch (RuntimeException exception) {
            LOG.warn(
                    "lesson playback falls back to the source object, transcode lookup failed:"
                            + " key={}",
                    sourceObjectKey,
                    exception);
        }
        return sourceObjectKey;
    }
}
