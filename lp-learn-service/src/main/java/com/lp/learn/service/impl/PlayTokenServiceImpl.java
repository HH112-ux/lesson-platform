package com.lp.learn.service.impl;

import com.lp.learn.access.LessonAccessGuard;
import com.lp.learn.client.LessonInternalClient;
import com.lp.learn.config.LearnProperties;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.dto.PlayTokenView;
import com.lp.learn.playback.PlayUrlSigner;
import com.lp.learn.service.PlayTokenService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Default {@link PlayTokenService} implementation.
 *
 * <p>Access is checked before signing: a paid lesson requires an active entitlement, a free trial
 * does not. The URL lifetime comes from configuration (ten minutes by default) and the expiry is
 * computed from an injected clock so it can be asserted in tests.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class PlayTokenServiceImpl implements PlayTokenService {

    private final LessonAccessGuard lessonAccessGuard;

    private final LessonInternalClient lessonInternalClient;

    private final PlayUrlSigner playUrlSigner;

    private final LearnProperties learnProperties;

    private final Clock clock;

    /**
     * Creates the service with its dependencies.
     *
     * @param lessonAccessGuard entitlement and free preview check
     * @param lessonInternalClient lesson media reader
     * @param playUrlSigner presigned URL producer
     * @param learnProperties playback URL lifetime
     * @param clock clock used to compute the expiry
     * @author Caspian
     * @since 0.1.0
     */
    public PlayTokenServiceImpl(
            LessonAccessGuard lessonAccessGuard,
            LessonInternalClient lessonInternalClient,
            PlayUrlSigner playUrlSigner,
            LearnProperties learnProperties,
            Clock clock) {
        this.lessonAccessGuard = lessonAccessGuard;
        this.lessonInternalClient = lessonInternalClient;
        this.playUrlSigner = playUrlSigner;
        this.learnProperties = learnProperties;
        this.clock = clock;
    }

    /**
     * Signs the lesson video for one student.
     *
     * @param userId student user id
     * @param lessonId lesson primary key
     * @return presigned URL and its expiry
     * @throws BizException with {@code 40401}, {@code 40302} or {@code 50206}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PlayTokenView issue(long userId, long lessonId) {
        LessonPlayInfo lesson = lessonInternalClient.lesson(lessonId);
        lessonAccessGuard.requireAccess(userId, lesson);
        Duration ttl = learnProperties.getPlayTokenTtl();
        String playUrl = playUrlSigner.presign(lesson.getVideoObjectKey(), ttl);
        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        return new PlayTokenView(playUrl, ttl.toSeconds(), expiresAt);
    }
}
