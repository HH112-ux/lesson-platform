package com.lp.course.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.course.config.LessonCleanupProperties;
import com.lp.course.mapper.LessonContentCleanupMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LessonContentCleanupWorkerTest {

    private static final String KEY = "courses/9/lessons/old.mp4";
    private static final LessonCleanupRow ROW = new LessonCleanupRow(7L, KEY, 0);
    private static final int BATCH_SIZE = 7;

    @Mock private LessonContentCleanupMapper mapper;
    @Mock private LessonCleanupObjectStore store;

    private LessonContentCleanupWorker worker;

    @BeforeEach
    void setUp() {
        worker =
                new LessonContentCleanupWorker(
                        mapper,
                        store,
                        new LessonCleanupProperties(
                                true,
                                BATCH_SIZE,
                                5,
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(10),
                                Duration.ofMinutes(10),
                                Duration.ofMinutes(10)),
                        new LessonCleanupMetrics(mapper, new SimpleMeterRegistry()));
        lenient().when(mapper.selectPending(any(), eq(BATCH_SIZE))).thenReturn(List.of(ROW));
        lenient().when(mapper.claim(7L)).thenReturn(1);
    }

    @Test
    void defersWhenLessonStillReferencesObject() {
        when(mapper.countLessonReferences(KEY)).thenReturn(1);

        worker.tick();

        verify(mapper).defer(eq(7L), any(LocalDateTime.class));
        verify(store, never()).delete(anyString());
        verify(mapper, never()).scheduleRetry(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void defersWhenUploadSessionStillReferencesObject() {
        when(mapper.countPendingUploads(KEY)).thenReturn(1);

        worker.tick();

        verify(mapper).defer(eq(7L), any(LocalDateTime.class));
        verify(store, never()).delete(anyString());
    }

    @Test
    void defersWhenTranscodeStillReadsObject() {
        when(mapper.countActiveTranscodes(KEY)).thenReturn(1);

        worker.tick();

        verify(mapper).defer(eq(7L), any(LocalDateTime.class));
        verify(store, never()).delete(anyString());
    }

    @Test
    void deletesAndMarksDoneWhenNothingReferencesObject() {
        worker.tick();

        verify(store).delete(KEY);
        verify(mapper).markDone(7L);
        verify(mapper, never()).defer(anyLong(), any());
    }

    @Test
    void retriesTransientFailureWithBackoff() {
        doThrow(new LessonCleanupObjectStore.CleanupFailure(false, "timeout"))
                .when(store)
                .delete(KEY);
        LocalDateTime earliest = LocalDateTime.now().plusSeconds(25);

        worker.tick();

        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).scheduleRetry(eq(7L), eq(1), retryAt.capture(), eq("timeout"));
        assertFalse(retryAt.getValue().isBefore(earliest));
        assertTrue(retryAt.getValue().isBefore(earliest.plusSeconds(20)));
    }

    @Test
    void failsImmediatelyOnPermanentError() {
        doThrow(new LessonCleanupObjectStore.CleanupFailure(true, "AccessDenied"))
                .when(store)
                .delete(KEY);

        worker.tick();

        verify(mapper).markFailed(7L, 1, "AccessDenied");
        verify(mapper, never()).scheduleRetry(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void failsWhenAttemptsAreExhausted() {
        when(mapper.selectPending(any(), eq(BATCH_SIZE)))
                .thenReturn(List.of(new LessonCleanupRow(7L, KEY, 4)));
        doThrow(new LessonCleanupObjectStore.CleanupFailure(false, "timeout"))
                .when(store)
                .delete(KEY);

        worker.tick();

        verify(mapper).markFailed(7L, 5, "timeout");
        verify(mapper, never()).scheduleRetry(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void skipsRowWhenClaimLosesTheRace() {
        when(mapper.claim(7L)).thenReturn(0);

        assertEquals(0, worker.tick());
        verify(store, never()).delete(anyString());
        verify(mapper, never()).markDone(anyLong());
    }

    @Test
    void reclaimsRowsAbandonedByACrashedTick() {
        worker.tick();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).reclaimStale(threshold.capture());
        assertTrue(threshold.getValue().isBefore(LocalDateTime.now().minusMinutes(9)));
    }

    @Test
    void keepsProcessingTheBatchWhenOneRowThrows() {
        String other = "courses/9/lessons/new.mp4";
        when(mapper.selectPending(any(), eq(BATCH_SIZE)))
                .thenReturn(List.of(ROW, new LessonCleanupRow(8L, other, 0)));
        when(mapper.claim(8L)).thenReturn(1);
        when(mapper.countLessonReferences(KEY)).thenThrow(new IllegalStateException("boom"));

        assertEquals(2, worker.tick());

        verify(store).delete(other);
        verify(mapper).markDone(8L);
        verify(mapper, never()).markDone(7L);
    }

    @Test
    void treatsMissingObjectAsAlreadyDeleted() {
        assertTrue(LessonCleanupObjectStore.isMissing("NoSuchKey", 404));
        assertTrue(LessonCleanupObjectStore.isMissing(null, 404));
        assertFalse(LessonCleanupObjectStore.isMissing("AccessDenied", 403));
        assertFalse(LessonCleanupObjectStore.isMissing("NoSuchBucket", 404));
    }
}
