package com.lp.course.upload;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

/**
 * Unit tests for enqueueing transcode tasks after a confirmation.
 *
 * @author Caspian
 * @since 0.11.0
 */
class LessonTranscodeServiceTest {

    private static final long LESSON_ID = 7L;

    private static final long UPLOAD_ID = 501L;

    private static final long TASK_ID = 41L;

    private static final String SOURCE_KEY = "lesson-videos/7/501.mp4";

    private LessonVideoTranscodeMapper mapper;

    private LessonTranscodeWorker worker;

    private SimpleMeterRegistry meterRegistry;

    private LessonTranscodeService service;

    @BeforeEach
    void setUp() {
        mapper = mock(LessonVideoTranscodeMapper.class);
        worker = mock(LessonTranscodeWorker.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new LessonTranscodeService(mapper, worker, properties(true), meterRegistry);
    }

    @Test
    void newTaskIsRecordedAndSubmitted() {
        when(mapper.insertIfAbsent(any())).thenAnswer(withId(TASK_ID));

        LessonVideoTranscode task = service.enqueue(LESSON_ID, UPLOAD_ID, SOURCE_KEY, 1024L);

        assertEquals(TASK_ID, task.getId());
        assertEquals(LessonTranscodeStatus.PENDING, task.getStatus());
        verify(worker, times(1)).run(TASK_ID);
    }

    @Test
    void existingTaskIsReturnedWithoutSubmittingAgain() {
        LessonVideoTranscode existing = new LessonVideoTranscode();
        existing.setId(TASK_ID);
        existing.setStatus(LessonTranscodeStatus.READY);
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.selectBySourceObjectKey(SOURCE_KEY)).thenReturn(existing);

        LessonVideoTranscode task = service.enqueue(LESSON_ID, UPLOAD_ID, SOURCE_KEY, 1024L);

        assertEquals(TASK_ID, task.getId());
        verify(worker, never()).run(anyLong());
    }

    @Test
    void insertFailureIsCountedAndNotPropagated() {
        when(mapper.insertIfAbsent(any())).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> service.enqueue(LESSON_ID, UPLOAD_ID, SOURCE_KEY, 1024L));

        assertEquals(
                1.0, meterRegistry.counter(LessonTranscodeService.ENQUEUE_FAILURE_METRIC).count());
        verify(worker, never()).run(anyLong());
    }

    @Test
    void rejectedSubmissionMarksTheTaskFailed() {
        when(mapper.insertIfAbsent(any())).thenAnswer(withId(TASK_ID));
        doThrow(new TaskRejectedException("full")).when(worker).run(TASK_ID);

        assertDoesNotThrow(() -> service.enqueue(LESSON_ID, UPLOAD_ID, SOURCE_KEY, 1024L));

        verify(mapper, times(1))
                .markFailed(eq(TASK_ID), eq(LessonTranscodeService.REJECTED_REASON), any());
        assertEquals(
                1.0, meterRegistry.counter(LessonTranscodeService.ENQUEUE_FAILURE_METRIC).count());
    }

    @Test
    void disabledServiceRecordsNothing() {
        service = new LessonTranscodeService(mapper, worker, properties(false), meterRegistry);

        assertNull(service.enqueue(LESSON_ID, UPLOAD_ID, SOURCE_KEY, 1024L));

        verifyNoInteractions(mapper, worker);
    }

    @Test
    void blankSourceKeyRecordsNothing() {
        assertNull(service.enqueue(LESSON_ID, UPLOAD_ID, " ", 1024L));

        verifyNoInteractions(mapper, worker);
    }

    private static org.mockito.stubbing.Answer<Integer> withId(long id) {
        return invocation -> {
            LessonVideoTranscode task = invocation.getArgument(0);
            task.setId(id);
            return 1;
        };
    }

    private static LessonTranscodeProperties properties(boolean enabled) {
        return new LessonTranscodeProperties(
                enabled,
                1,
                4,
                "ffmpeg",
                Duration.ofMinutes(1),
                3,
                Duration.ZERO,
                Duration.ofMinutes(1),
                Duration.ofMinutes(1));
    }
}
