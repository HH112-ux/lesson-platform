package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.access.LessonAccessGuard;
import com.lp.learn.client.LessonInternalClient;
import com.lp.learn.config.LearnProperties;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.dto.PlayTokenView;
import com.lp.learn.playback.PlayUrlSigner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for playback token issuing; storage, course-service and persistence are mocked.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class PlayTokenServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long LESSON_ID = 5L;

    private static final long COURSE_ID = 7L;

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private LessonAccessGuard lessonAccessGuard;

    @Mock private LessonInternalClient lessonInternalClient;

    @Mock private PlayUrlSigner playUrlSigner;

    private LearnProperties learnProperties;

    private PlayTokenServiceImpl playTokenService;

    /**
     * Builds the service under test with a fixed clock and ten minute lifetime.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        learnProperties = new LearnProperties();
        learnProperties.setPlayTokenTtl(Duration.ofMinutes(10));
        playTokenService =
                new PlayTokenServiceImpl(
                        lessonAccessGuard,
                        lessonInternalClient,
                        playUrlSigner,
                        learnProperties,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * An entitled student receives a signed URL valid for ten minutes.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void issuesTokenForEntitledUser() {
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson(false));
        when(playUrlSigner.presign(any(), any())).thenReturn("https://minio.local/lesson.mp4");

        PlayTokenView token = playTokenService.issue(USER_ID, LESSON_ID);

        assertEquals("https://minio.local/lesson.mp4", token.getPlayUrl());
        assertEquals(600, token.getExpiresIn());
        assertEquals(NOW.plusSeconds(600).getEpochSecond(), token.getExpiresAt());
        verify(playUrlSigner).presign("media/lesson-5.mp4", Duration.ofMinutes(10));
    }

    /**
     * A student without entitlement is rejected with {@code 40302} and nothing is signed.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsWithoutEntitlement() {
        LessonPlayInfo lesson = lesson(false);
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson);
        doThrow(new BizException(ErrorCode.LEARN_NOT_ENTITLED))
                .when(lessonAccessGuard)
                .requireAccess(USER_ID, lesson);

        BizException exception =
                assertThrows(BizException.class, () -> playTokenService.issue(USER_ID, LESSON_ID));

        assertEquals(ErrorCode.LEARN_NOT_ENTITLED.getCode(), exception.getCode());
        verify(playUrlSigner, never()).presign(any(), any());
    }

    /**
     * A missing lesson keeps the documented {@code 40401}.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void missingLessonFailsWith40401() {
        when(lessonInternalClient.lesson(LESSON_ID))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));

        BizException exception =
                assertThrows(BizException.class, () -> playTokenService.issue(USER_ID, LESSON_ID));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(lessonAccessGuard, never()).requireAccess(anyLong(), any());
    }

    /**
     * A free trial lesson is signed without an entitlement.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void freeLessonIssuesWithoutEntitlement() {
        LessonPlayInfo lesson = lesson(true);
        when(lessonInternalClient.lesson(LESSON_ID)).thenReturn(lesson);
        when(playUrlSigner.presign(any(), any())).thenReturn("https://minio.local/free.mp4");

        PlayTokenView token = playTokenService.issue(USER_ID, LESSON_ID);

        assertEquals("https://minio.local/free.mp4", token.getPlayUrl());
        verify(lessonAccessGuard).requireAccess(USER_ID, lesson);
    }

    private LessonPlayInfo lesson(boolean free) {
        LessonPlayInfo info = new LessonPlayInfo();
        info.setLessonId(LESSON_ID);
        info.setCourseId(COURSE_ID);
        info.setVideoObjectKey("media/lesson-5.mp4");
        info.setDurationSec(600);
        info.setFree(free);
        return info;
    }
}
