package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.course.config.LessonTranscodeProperties;
import com.lp.course.domain.LessonTranscodeStatus;
import com.lp.course.entity.LessonVideoTranscode;
import com.lp.course.mapper.LessonVideoTranscodeMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the retry and terminal state behavior of the transcode worker.
 *
 * @author Caspian
 * @since 0.11.0
 */
class LessonTranscodeWorkerTest {

    private static final long TASK_ID = 41L;

    private static final String SOURCE_KEY = "lesson-videos/7/9.mp4";

    private static final String OUTPUT_KEY = "transcoded/" + SOURCE_KEY;

    @TempDir Path tempDirectory;

    private LessonVideoTranscodeMapper mapper;

    private LessonTranscodeObjectStore objectStore;

    private LessonVideoTranscoder transcoder;

    private LessonTranscodeWorker worker;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        mapper = mock(LessonVideoTranscodeMapper.class);
        objectStore = mock(LessonTranscodeObjectStore.class);
        transcoder = mock(LessonVideoTranscoder.class);
        meterRegistry = new SimpleMeterRegistry();
        worker =
                new LessonTranscodeWorker(
                        mapper, objectStore, transcoder, properties(3), meterRegistry);
    }

    @Test
    void transcodesOnceAndMarksReady() throws Exception {
        when(mapper.selectById(TASK_ID)).thenReturn(task(LessonTranscodeStatus.PENDING, 0));
        when(mapper.markRunning(anyLong(), anyInt(), any())).thenReturn(1);
        Path source = Files.writeString(tempDirectory.resolve("source.mp4"), "source");
        when(objectStore.download(SOURCE_KEY)).thenReturn(source);
        when(objectStore.outputKey(SOURCE_KEY)).thenReturn(OUTPUT_KEY);
        when(mapper.markReady(anyLong(), anyString(), any())).thenReturn(1);

        worker.run(TASK_ID);

        verify(transcoder, times(1)).transcode(eq(source), any());
        verify(objectStore, times(1)).upload(eq(OUTPUT_KEY), any());
        verify(mapper, times(1)).markReady(eq(TASK_ID), eq(OUTPUT_KEY), any());
        verify(mapper, never()).markFailed(anyLong(), anyString(), any());
        assertEquals(0.0, meterRegistry.counter(LessonTranscodeWorker.FAILURE_METRIC).count());
    }

    @Test
    void retriesThreeTimesThenMarksFailedWithReason() {
        when(mapper.selectById(TASK_ID)).thenReturn(task(LessonTranscodeStatus.PENDING, 0));
        when(mapper.markRunning(anyLong(), anyInt(), any())).thenReturn(1);
        when(objectStore.download(SOURCE_KEY))
                .thenThrow(new IllegalStateException("storage unavailable"));

        worker.run(TASK_ID);

        verify(mapper, times(3)).markRunning(eq(TASK_ID), anyInt(), any());
        verify(mapper, times(1)).markFailed(eq(TASK_ID), eq("storage unavailable"), any());
        assertEquals(3.0, meterRegistry.counter(LessonTranscodeWorker.FAILURE_METRIC).count());
    }

    @Test
    void readyTaskIsSkipped() {
        when(mapper.selectById(TASK_ID)).thenReturn(task(LessonTranscodeStatus.READY, 1));

        worker.run(TASK_ID);

        verify(mapper, never()).markRunning(anyLong(), anyInt(), any());
        verify(objectStore, never()).download(anyString());
    }

    @Test
    void lostClaimStopsWithoutDownload() {
        when(mapper.selectById(TASK_ID)).thenReturn(task(LessonTranscodeStatus.PENDING, 0));
        when(mapper.markRunning(anyLong(), anyInt(), any())).thenReturn(0);

        worker.run(TASK_ID);

        verify(objectStore, never()).download(anyString());
        verify(mapper, never()).markFailed(anyLong(), anyString(), any());
    }

    @Test
    void exhaustedAttemptsAreClosedWithoutDownload() {
        when(mapper.selectById(TASK_ID)).thenReturn(task(LessonTranscodeStatus.RUNNING, 3));

        worker.run(TASK_ID);

        verify(mapper, times(1))
                .markFailed(eq(TASK_ID), eq("attempt limit already reached"), any());
        verify(objectStore, never()).download(anyString());
    }

    private static LessonVideoTranscode task(int status, int attemptCount) {
        LessonVideoTranscode task = new LessonVideoTranscode();
        task.setId(TASK_ID);
        task.setLessonId(7L);
        task.setSourceObjectKey(SOURCE_KEY);
        task.setStatus(status);
        task.setAttemptCount(attemptCount);
        return task;
    }

    private static LessonTranscodeProperties properties(int maxAttempts) {
        return new LessonTranscodeProperties(
                true,
                1,
                4,
                "ffmpeg",
                Duration.ofMinutes(1),
                maxAttempts,
                Duration.ZERO,
                Duration.ofMinutes(1),
                Duration.ofMinutes(1));
    }
}
