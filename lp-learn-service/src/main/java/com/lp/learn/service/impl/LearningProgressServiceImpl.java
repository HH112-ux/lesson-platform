package com.lp.learn.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.access.LessonAccessGuard;
import com.lp.learn.client.LessonInternalClient;
import com.lp.learn.dto.CourseProgressView;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.dto.LessonProgressView;
import com.lp.learn.entity.LearningProgress;
import com.lp.learn.mapper.LearningProgressMapper;
import com.lp.learn.service.LearningProgressService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link LearningProgressService} implementation.
 *
 * <p>{@code max_position_seconds} never decreases, is used for resume, and also drives completion
 * together with {@code watched_sec}: a lesson counts as complete only when the accumulated credible
 * watch time and the furthest playback position both reach 90% of its duration, so neither seeking
 * to the end nor leaving the player muted can complete a lesson on its own.
 *
 * <p>Credible increment: the server uses the gap between the previous stored {@code updated_at} and
 * now (never a client supplied interval, which could be forged) as the real elapsed time. The
 * playback advance is the difference to the previously reported position; it is clamped to {@code
 * elapsed * 1.5} so a forward seek can never earn more than 1.5x real time, and a rewind earns
 * nothing. Reports with a position below 2 seconds or an interval below 5 seconds are treated as
 * abnormal heartbeats and add nothing.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class LearningProgressServiceImpl implements LearningProgressService {

    private static final int COMPLETION_PERCENT = 90;

    private static final long PERCENT_BASE = 100L;

    /** Positions below this value are treated as idle heartbeats. */
    private static final int MIN_COUNTABLE_POSITION_SECONDS = 2;

    /** Reports closer than this to the previous one are not trusted. */
    private static final long MIN_COUNTABLE_INTERVAL_SECONDS = 5L;

    /** A report can earn at most this multiple of the real elapsed time. */
    private static final int TRUSTED_RATE_NUMERATOR = 3;

    private static final int TRUSTED_RATE_DENOMINATOR = 2;

    private final LessonInternalClient lessonInternalClient;

    private final LessonAccessGuard lessonAccessGuard;

    private final LearningProgressMapper learningProgressMapper;

    private final Clock clock;

    /**
     * Creates the service with its dependencies.
     *
     * @param lessonInternalClient lesson metadata reader
     * @param lessonAccessGuard entitlement and free preview check
     * @param learningProgressMapper progress persistence
     * @author Caspian
     * @since 0.1.0
     */
    @Autowired
    public LearningProgressServiceImpl(
            LessonInternalClient lessonInternalClient,
            LessonAccessGuard lessonAccessGuard,
            LearningProgressMapper learningProgressMapper) {
        this(
                lessonInternalClient,
                lessonAccessGuard,
                learningProgressMapper,
                Clock.systemDefaultZone());
    }

    /**
     * Creates the service with an explicit clock, used to make the elapsed interval deterministic.
     *
     * @param lessonInternalClient lesson metadata reader
     * @param lessonAccessGuard entitlement and free preview check
     * @param learningProgressMapper progress persistence
     * @param clock clock used to compute the interval since the previous report
     * @author Caspian
     * @since 0.1.0
     */
    LearningProgressServiceImpl(
            LessonInternalClient lessonInternalClient,
            LessonAccessGuard lessonAccessGuard,
            LearningProgressMapper learningProgressMapper,
            Clock clock) {
        this.lessonInternalClient = lessonInternalClient;
        this.lessonAccessGuard = lessonAccessGuard;
        this.learningProgressMapper = learningProgressMapper;
        this.clock = clock;
    }

    /**
     * Records one position report.
     *
     * @param userId student user id
     * @param lessonId lesson id
     * @param positionSeconds reported position
     * @return stored progress with the server computed watch time and completion flag
     * @throws BizException with {@code 40401}, {@code 40302} or {@code 63001}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonProgressView report(long userId, long lessonId, int positionSeconds) {
        LessonPlayInfo lesson = lessonInternalClient.lesson(lessonId);
        lessonAccessGuard.requireAccess(userId, lesson);
        if (positionSeconds < 0
                || (lesson.getDurationSec() > 0 && positionSeconds > lesson.getDurationSec())) {
            throw new BizException(ErrorCode.LEARN_BIZ_ERROR);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LearningProgress existing = learningProgressMapper.selectOne(userId, lessonId);
        int maxPositionSeconds =
                existing == null || existing.getMaxPositionSeconds() == null
                        ? positionSeconds
                        : Math.max(existing.getMaxPositionSeconds(), positionSeconds);

        long elapsedSeconds = elapsedSeconds(existing, now);
        long limit = elapsedSeconds * TRUSTED_RATE_NUMERATOR / TRUSTED_RATE_DENOMINATOR;
        int advance =
                existing == null || existing.getPositionSeconds() == null
                        ? 0
                        : Math.max(0, positionSeconds - existing.getPositionSeconds());
        boolean truncated = advance > limit;
        long credited = Math.min(advance, limit);
        if (positionSeconds < MIN_COUNTABLE_POSITION_SECONDS
                || elapsedSeconds < MIN_COUNTABLE_INTERVAL_SECONDS) {
            credited = 0;
            truncated = false;
        }

        int previousWatched =
                existing == null || existing.getWatchedSeconds() == null
                        ? 0
                        : existing.getWatchedSeconds();
        int watchedSeconds = (int) Math.min(Integer.MAX_VALUE, previousWatched + credited);
        boolean completed =
                (existing != null
                                && existing.getCompleted() != null
                                && existing.getCompleted() == 1)
                        || (reachedThreshold(watchedSeconds, lesson.getDurationSec())
                                && reachedThreshold(maxPositionSeconds, lesson.getDurationSec()));

        LearningProgress progress = new LearningProgress();
        progress.setUserId(userId);
        progress.setCourseId(lesson.getCourseId());
        progress.setLessonId(lessonId);
        progress.setPositionSeconds(positionSeconds);
        progress.setMaxPositionSeconds(maxPositionSeconds);
        progress.setWatchedSeconds(watchedSeconds);
        progress.setCompleted(completed ? 1 : 0);
        learningProgressMapper.merge(progress);
        return new LessonProgressView(
                lessonId,
                positionSeconds,
                maxPositionSeconds,
                watchedSeconds,
                completed,
                truncated,
                now);
    }

    /**
     * Returns the student's progress for one course.
     *
     * @param userId student user id
     * @param courseId course id
     * @return reported lessons, possibly empty
     * @throws BizException with {@code 40302} when the course is not owned
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CourseProgressView courseProgress(long userId, long courseId) {
        lessonAccessGuard.requireCourseAccess(userId, courseId);
        List<LessonProgressView> lessons =
                learningProgressMapper.selectByUserCourse(userId, courseId).stream()
                        .map(
                                row ->
                                        new LessonProgressView(
                                                row.getLessonId(),
                                                row.getPositionSeconds(),
                                                row.getMaxPositionSeconds(),
                                                row.getWatchedSeconds(),
                                                row.getCompleted() != null
                                                        && row.getCompleted() == 1,
                                                false,
                                                row.getUpdatedAt()))
                        .toList();
        return new CourseProgressView(courseId, lessons);
    }

    private static long elapsedSeconds(LearningProgress existing, LocalDateTime now) {
        if (existing == null || existing.getUpdatedAt() == null) {
            return 0L;
        }
        long seconds = Duration.between(existing.getUpdatedAt(), now).getSeconds();
        return Math.max(0L, seconds);
    }

    private static boolean reachedThreshold(int seconds, int durationSec) {
        return durationSec > 0 && seconds * PERCENT_BASE >= (long) durationSec * COMPLETION_PERCENT;
    }
}
