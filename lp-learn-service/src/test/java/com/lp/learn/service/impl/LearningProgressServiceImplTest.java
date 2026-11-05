package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.client.LessonInternalClient;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.dto.LessonProgressView;
import com.lp.learn.entity.LearningProgress;
import com.lp.learn.mapper.LearningProgressMapper;
import com.lp.learn.service.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for progress reporting; lesson metadata and persistence are mocked.
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

    @Mock private LessonInternalClient lessonInternalClient;

    @Mock private EntitlementService entitlementService;

    @Mock private LearningProgressMapper learningProgressMapper;

    private LearningProgressServiceImpl progressService;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        progressService =
                new LearningProgressServiceImpl(
                        lessonInternalClient, entitlementService, learningProgressMapper);
    }

    /**
     * A first report stores the position and leaves the lesson incomplete.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void reportsPositionForEntitledUser() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(true);

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 100);

        assertEquals(100, view.getPositionSeconds());
        assertEquals(100, view.getMaxPositionSeconds());
        assertFalse(view.isCompleted());
        LearningProgress saved = captureMerged();
        assertEquals(100, saved.getMaxPositionSeconds());
        assertEquals(0, saved.getCompleted());
    }

    /**
     * Rewinding updates the latest position but never lowers the stored maximum.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rewindKeepsMaxPosition() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(true);
        when(learningProgressMapper.selectOne(USER_ID, LESSON_ID)).thenReturn(existing(300, 0));

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 120);

        assertEquals(120, view.getPositionSeconds());
        assertEquals(300, view.getMaxPositionSeconds());
        LearningProgress saved = captureMerged();
        assertEquals(120, saved.getPositionSeconds());
        assertEquals(300, saved.getMaxPositionSeconds());
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
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(true);

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
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(false);

        BizException exception =
                assertThrows(
                        BizException.class, () -> progressService.report(USER_ID, LESSON_ID, 10));

        assertEquals(ErrorCode.LEARN_NOT_ENTITLED.getCode(), exception.getCode());
        verify(learningProgressMapper, never()).merge(any());
    }

    /**
     * Reaching 90% of the duration flips completion on the server.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void completionIsTriggeredByThreshold() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(true);

        LessonProgressView view = progressService.report(USER_ID, LESSON_ID, 540);

        assertTrue(view.isCompleted());
        LearningProgress saved = captureMerged();
        assertEquals(1, saved.getCompleted());
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
        return info;
    }

    private LearningProgress existing(int maxPosition, int completed) {
        LearningProgress progress = new LearningProgress();
        progress.setUserId(USER_ID);
        progress.setCourseId(COURSE_ID);
        progress.setLessonId(LESSON_ID);
        progress.setPositionSeconds(maxPosition);
        progress.setMaxPositionSeconds(maxPosition);
        progress.setCompleted(completed);
        return progress;
    }
}
