package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lp.course.domain.LessonTranscodeStatus;
import com.lp.course.entity.LessonVideoTranscode;
import com.lp.course.mapper.LessonVideoTranscodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for choosing the playback key.
 *
 * @author Caspian
 * @since 0.11.0
 */
class LessonPlaybackKeyResolverTest {

    private static final String SOURCE_KEY = "lesson-videos/7/501.mp4";

    private static final String OUTPUT_KEY = "transcoded/lesson-videos/7/501.mp4";

    private LessonVideoTranscodeMapper mapper;

    private LessonPlaybackKeyResolver resolver;

    @BeforeEach
    void setUp() {
        mapper = mock(LessonVideoTranscodeMapper.class);
        resolver = new LessonPlaybackKeyResolver(mapper);
    }

    @Test
    void readyTaskUsesTheTranscodedKey() {
        when(mapper.selectBySourceObjectKey(SOURCE_KEY))
                .thenReturn(task(LessonTranscodeStatus.READY, OUTPUT_KEY));

        assertEquals(OUTPUT_KEY, resolver.resolve(SOURCE_KEY));
    }

    @Test
    void pendingTaskFallsBackToTheSourceKey() {
        when(mapper.selectBySourceObjectKey(SOURCE_KEY))
                .thenReturn(task(LessonTranscodeStatus.PENDING, null));

        assertEquals(SOURCE_KEY, resolver.resolve(SOURCE_KEY));
    }

    @Test
    void failedTaskFallsBackToTheSourceKey() {
        when(mapper.selectBySourceObjectKey(SOURCE_KEY))
                .thenReturn(task(LessonTranscodeStatus.FAILED, null));

        assertEquals(SOURCE_KEY, resolver.resolve(SOURCE_KEY));
    }

    @Test
    void readyTaskWithoutOutputKeyFallsBackToTheSourceKey() {
        when(mapper.selectBySourceObjectKey(SOURCE_KEY))
                .thenReturn(task(LessonTranscodeStatus.READY, " "));

        assertEquals(SOURCE_KEY, resolver.resolve(SOURCE_KEY));
    }

    @Test
    void missingTaskFallsBackToTheSourceKey() {
        assertEquals(SOURCE_KEY, resolver.resolve(SOURCE_KEY));
    }

    @Test
    void lookupFailureFallsBackToTheSourceKey() {
        when(mapper.selectBySourceObjectKey(SOURCE_KEY))
                .thenThrow(new IllegalStateException("database down"));

        assertEquals(SOURCE_KEY, resolver.resolve(SOURCE_KEY));
    }

    @Test
    void nullKeyStaysNullWithoutALookup() {
        assertNull(resolver.resolve(null));
    }

    private static LessonVideoTranscode task(int status, String outputObjectKey) {
        LessonVideoTranscode task = new LessonVideoTranscode();
        task.setSourceObjectKey(SOURCE_KEY);
        task.setOutputObjectKey(outputObjectKey);
        task.setStatus(status);
        return task;
    }
}
