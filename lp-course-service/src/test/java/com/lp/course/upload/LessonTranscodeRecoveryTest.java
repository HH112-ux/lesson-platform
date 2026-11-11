package com.lp.course.upload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.course.config.LessonTranscodeProperties;
import com.lp.course.domain.LessonTranscodeStatus;
import com.lp.course.entity.LessonVideoTranscode;
import com.lp.course.mapper.LessonVideoTranscodeMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

/**
 * Unit tests for the startup recovery of interrupted transcode tasks.
 *
 * @author Caspian
 * @since 0.11.0
 */
class LessonTranscodeRecoveryTest {

    private static final long FIRST = 41L;

    private static final long SECOND = 42L;

    private LessonVideoTranscodeMapper mapper;

    private LessonTranscodeWorker worker;

    private LessonTranscodeRecovery recovery;

    @BeforeEach
    void setUp() {
        mapper = mock(LessonVideoTranscodeMapper.class);
        worker = mock(LessonTranscodeWorker.class);
        recovery = new LessonTranscodeRecovery(mapper, worker, properties(true, 3));
        when(mapper.selectRunningIds()).thenReturn(List.of());
        when(mapper.selectPendingIds()).thenReturn(List.of());
    }

    @Test
    void runningTaskBelowLimitIsRequeuedAndResubmitted() {
        when(mapper.selectRunningIds()).thenReturn(List.of(FIRST));
        when(mapper.selectById(FIRST)).thenReturn(task(LessonTranscodeStatus.RUNNING, 1));
        when(mapper.requeueAfterRestart(FIRST, 1)).thenReturn(1);

        recovery.recover();

        verify(mapper, times(1)).requeueAfterRestart(FIRST, 1);
        verify(worker, times(1)).run(FIRST);
        verify(mapper, never()).markFailed(anyLong(), anyString(), any());
    }

    @Test
    void runningTaskAtLimitIsMarkedFailed() {
        when(mapper.selectRunningIds()).thenReturn(List.of(FIRST));
        when(mapper.selectById(FIRST)).thenReturn(task(LessonTranscodeStatus.RUNNING, 3));

        recovery.recover();

        verify(mapper, times(1))
                .markFailed(eq(FIRST), eq(LessonTranscodeRecovery.EXHAUSTED_REASON), any());
        verify(mapper, never()).requeueAfterRestart(anyLong(), anyInt());
        verify(worker, never()).run(anyLong());
    }

    @Test
    void pendingTasksAreResubmitted() {
        when(mapper.selectPendingIds()).thenReturn(List.of(FIRST, SECOND));

        recovery.recover();

        verify(worker, times(1)).run(FIRST);
        verify(worker, times(1)).run(SECOND);
        verify(mapper, never()).markFailed(anyLong(), anyString(), any());
    }

    @Test
    void rejectedSubmissionIsMarkedFailed() {
        when(mapper.selectPendingIds()).thenReturn(List.of(FIRST));
        doThrow(new TaskRejectedException("full")).when(worker).run(FIRST);

        recovery.recover();

        verify(mapper, times(1))
                .markFailed(eq(FIRST), eq(LessonTranscodeRecovery.REJECTED_REASON), any());
    }

    @Test
    void oneBadTaskDoesNotStopTheScan() {
        when(mapper.selectRunningIds()).thenReturn(List.of(FIRST, SECOND));
        when(mapper.selectById(FIRST)).thenThrow(new IllegalStateException("database hiccup"));
        when(mapper.selectById(SECOND)).thenReturn(task(LessonTranscodeStatus.RUNNING, 0));
        when(mapper.requeueAfterRestart(SECOND, 0)).thenReturn(1);

        recovery.recover();

        verify(worker, times(1)).run(SECOND);
        verify(worker, never()).run(FIRST);
    }

    @Test
    void disabledSkipsRecovery() {
        recovery = new LessonTranscodeRecovery(mapper, worker, properties(false, 3));

        recovery.recover();

        verifyNoInteractions(mapper, worker);
    }

    private static LessonVideoTranscode task(int status, int attemptCount) {
        LessonVideoTranscode task = new LessonVideoTranscode();
        task.setId(FIRST);
        task.setLessonId(7L);
        task.setSourceObjectKey("lesson-videos/7/9.mp4");
        task.setStatus(status);
        task.setAttemptCount(attemptCount);
        return task;
    }

    private static LessonTranscodeProperties properties(boolean enabled, int maxAttempts) {
        return new LessonTranscodeProperties(
                enabled,
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
