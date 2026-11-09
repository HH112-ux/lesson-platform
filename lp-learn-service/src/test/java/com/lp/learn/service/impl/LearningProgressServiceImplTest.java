package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.access.LessonAccessGuard;
import com.lp.learn.client.LessonInternalClient;
import com.lp.learn.dto.CourseProgressView;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.dto.LessonProgressView;
import com.lp.learn.entity.LearningProgress;
import com.lp.learn.mapper.LearningProgressMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for progress reporting; lesson metadata and persistence are mocked.
 *
 * <p>The clock is fixed so the trusted increment bound is deterministic.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class LearningProgressServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long LESSON_ID = 5L;

    private static final long COURSE_ID = 7L;

    private static final int DURATION_SEC = 600;

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-03-01T10:00:10Z"), ZoneOffset.UTC);

    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());

    @Mock private LessonInternalClient lessonInternalClient;

    @Mock private LessonAccessGuard lessonAccessGuard;

    @Mock private LearningProgressMapper learningProgressMapper;

    private LearningProgressServiceImpl progressService;

    /**
     * Builds the service under test with the fixed clock.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        progressService =
                new LearningProgressServiceImpl(
                        lessonInternalClient, lessonAccessGuard, learningProgressMapper, CLOCK);
    }

    /**
     * A first report stores the position but cannot credit watch time without a baseline.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void firstReportStoresPositionWithoutWatchedTime() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 100);

        assertEquals(100, view.getPositionSeconds());
        assertEquals(0, view.getWatchedSeconds());
        assertFalse(view.isCompleted());
        assertFalse(view.isTruncated());
    }

    /**
     * A regular heartbeat credits the playback advance when it fits the trusted bound.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void normalHeartbeatAccumulatesWatchedTime() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(100, 100, 100, 0, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 110);

        // 10 seconds elapsed, advance 10 <= 15, so all of it is credible.
        assertEquals(110, view.getWatchedSeconds());
        assertFalse(view.isTruncated());
        assertEquals(110, captureMerged().getWatchedSeconds());
    }

    /**
     * Dragging backwards never earns watch time and never lowers the maximum position.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rewindDoesNotAddWatchedTimeOrLowerMax() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(300, 300, 300, 0, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 120);

        assertEquals(120, view.getPositionSeconds());
        assertEquals(300, view.getMaxPositionSeconds());
        assertEquals(300, view.getWatchedSeconds());
        assertFalse(view.isTruncated());
    }

    /**
     * A forward seek is capped at 1.5x the real elapsed time and flagged as truncated.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void advanceBeyondTrustedBoundIsTruncated() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(10, 10, 10, 0, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 310);

        // 10 seconds elapsed allows 15 seconds; the 300 second jump is cut to 15.
        assertEquals(25, view.getWatchedSeconds());
        assertTrue(view.isTruncated());
        assertEquals(310, view.getMaxPositionSeconds());
    }

    /**
     * Reports closer than five seconds apart are treated as abnormal and add nothing.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void shortIntervalIsNotCounted() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(100, 100, 100, 0, 2));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 110);

        assertEquals(100, view.getWatchedSeconds());
        assertFalse(view.isTruncated());
    }

    /**
     * Reaching the end of the lesson does not complete it when the credible watch time is short, so
     * the playback position is a necessary condition but not a sufficient one.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void positionAtThresholdWithShortWatchIsNotComplete() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(595, 595, 530, 0, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 600);

        assertEquals(535, view.getWatchedSeconds());
        assertFalse(view.isCompleted());
    }

    /**
     * Accumulated watch time alone does not complete a lesson when the furthest position stays
     * below the threshold, so muting the player cannot mark the lesson as learned.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void watchedAtThresholdWithShortPositionIsNotComplete() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(100, 100, 538, 0, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 539);

        assertEquals(553, view.getWatchedSeconds());
        assertEquals(539, view.getMaxPositionSeconds());
        assertFalse(view.isCompleted());
        assertEquals(0, captureMerged().getCompleted());
    }

    /**
     * Both thresholds reaching exactly 90% of the duration flip completion.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void exactThresholdsTriggerCompletion() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(538, 538, 538, 0, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 540);

        assertEquals(540, view.getWatchedSeconds());
        assertEquals(540, view.getMaxPositionSeconds());
        assertTrue(view.isCompleted());
        assertEquals(1, captureMerged().getCompleted());
    }

    /**
     * Both thresholds reaching 90% of the duration flips completion.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void bothThresholdsTriggerCompletion() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(538, 538, 538, 0, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 545);

        assertEquals(545, view.getWatchedSeconds());
        assertTrue(view.isCompleted());
        assertEquals(1, captureMerged().getCompleted());
    }

    /**
     * A lesson already marked complete stays complete even when later reports do not reach the
     * thresholds again.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void completedStateNeverReverts() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(100, 100, 0, 1, 10));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 110);

        assertTrue(view.isCompleted());
        assertEquals(1, captureMerged().getCompleted());
    }

    /**
     * Re-sending the same position adds nothing, so a retried heartbeat cannot inflate watch time.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void duplicateReportDoesNotAccumulateWatchedTime() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID))
                .thenReturn(existing(110, 110, 110, 0, 30));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 110);

        assertEquals(110, view.getWatchedSeconds());
        assertFalse(view.isTruncated());
        assertEquals(110, captureMerged().getWatchedSeconds());
    }

    /**
     * Course progress is gated only by the entitlement, so a course taken offline still lets its
     * owners read the progress they already recorded.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void courseProgressOnlyRequiresEntitlement() {
        when(learningProgressMapper.selectByUserCourse(USER_ID, COURSE_ID))
                .thenReturn(List.of(existing(120, 300, 300, 1, 10)));

        CourseProgressView view = progressService.courseProgress(USER_ID, COURSE_ID);

        assertEquals(COURSE_ID, view.getCourseId());
        assertEquals(1, view.getLessons().size());
        assertTrue(view.getLessons().get(0).isCompleted());
        verify(lessonAccessGuard).requireCourseAccess(USER_ID, COURSE_ID);
    }

    /**
     * A position beyond the lesson duration is rejected with {@code 63001}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void outOfRangeIsRejected() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));

        BizException exception =
                assertThrows(
                        BizException.class,
                        () -> progressService.report(USER_ID, LESSON_ID, DURATION_SEC + 1));

        assertEquals(ErrorCode.LEARN_BIZ_ERROR.getCode(), exception.getCode());
        verify(learningProgressMapper, never()).merge(any());
    }

    /**
     * A user without entitlement is rejected, even if another user owns the course.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void crossUserAccessIsRejected() {
        LessonPlayInfo lesson = lesson(false);
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson);
        doThrow(new BizException(ErrorCode.LEARN_NOT_ENTITLED))
                .when(lessonAccessGuard)
                .requireAccess(USER_ID, lesson);

        BizException exception =
                assertThrows(
                        BizException.class, () -> progressService.report(USER_ID, LESSON_ID, 10));

        assertEquals(ErrorCode.LEARN_NOT_ENTITLED.getCode(), exception.getCode());
        verify(learningProgressMapper, never()).merge(any());
    }

    private LearningProgress captureMerged() {
        ArgumentCaptor<LearningProgress> captor = ArgumentCaptor.forClass(LearningProgress.class);
        verify(learningProgressMapper).merge(captor.capture());
        return captor.getValue();
    }

    private LessonPlayInfo lesson(boolean free) {
        LessonPlayInfo info = new LessonPlayInfo();
        info.setLessonId(LESSON_ID);
        info.setCourseId(COURSE_ID);
        info.setVideoObjectKey("media/lesson-5.mp4");
        info.setDurationSec(DURATION_SEC);
        info.setFree(free);
        info.setCourseStatus(2);
        return info;
    }

    private LearningProgress existing(
            int position, int maxPosition, int watched, int completed, int secondsAgo) {
        LearningProgress progress = new LearningProgress();
        progress.setUserId(USER_ID);
        progress.setCourseId(COURSE_ID);
        progress.setLessonId(LESSON_ID);
        progress.setPositionSeconds(position);
        progress.setMaxPositionSeconds(maxPosition);
        progress.setWatchedSeconds(watched);
        progress.setCompleted(completed);
        progress.setUpdatedAt(NOW.minusSeconds(secondsAgo));
        return progress;
    }
}
