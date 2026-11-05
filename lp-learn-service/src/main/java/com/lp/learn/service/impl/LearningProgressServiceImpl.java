package com.lp.learn.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.client.LessonInternalClient;
import com.lp.learn.dto.CourseProgressView;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.dto.LessonProgressView;
import com.lp.learn.entity.LearningProgress;
import com.lp.learn.mapper.LearningProgressMapper;
import com.lp.learn.service.EntitlementService;
import com.lp.learn.service.LearningProgressService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link LearningProgressService} implementation.
 *
 * <p>{@code max_position_seconds} never decreases, and {@code completed} is computed here from the
 * lesson duration: a client can rewind freely but cannot mark a lesson complete. Completion needs
 * the maximum position to reach 90% of the duration; once set it is never cleared.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class LearningProgressServiceImpl implements LearningProgressService {

    private static final int COMPLETION_PERCENT = 90;

    private static final long PERCENT_BASE = 100L;

    private final LessonInternalClient lessonInternalClient;

    private final EntitlementService entitlementService;

    private final LearningProgressMapper learningProgressMapper;

    /**
     * Creates the service with its dependencies.
     *
     * @param lessonInternalClient lesson metadata reader
     * @param entitlementService ownership check
     * @param learningProgressMapper progress persistence
     * @author Caspian
     * @since 0.1.0
     */
    public LearningProgressServiceImpl(
            LessonInternalClient lessonInternalClient,
            EntitlementService entitlementService,
            LearningProgressMapper learningProgressMapper) {
        this.lessonInternalClient = lessonInternalClient;
        this.entitlementService = entitlementService;
        this.learningProgressMapper = learningProgressMapper;
    }

    /**
     * Records one position report.
     *
     * @param userId student user id
     * @param lessonId lesson id
     * @param positionSeconds reported position
     * @return stored progress with the server computed completion flag
     * @throws BizException with {@code 40401}, {@code 40302} or {@code 63001}
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonProgressView report(long userId, long lessonId, int positionSeconds) {
        LessonPlayInfo lesson = lessonInternalClient.lesson(lessonId);
        if (!lesson.isFree() && !entitlementService.hasEntitlement(userId, lesson.getCourseId())) {
            throw new BizException(ErrorCode.LEARN_NOT_ENTITLED);
        }
        if (positionSeconds < 0
                || (lesson.getDurationSec() > 0 && positionSeconds > lesson.getDurationSec())) {
            throw new BizException(ErrorCode.LEARN_BIZ_ERROR);
        }

        LearningProgress existing = learningProgressMapper.selectOne(userId, lessonId);
        int maxPositionSeconds =
                existing == null
                        ? positionSeconds
                        : Math.max(existing.getMaxPositionSeconds(), positionSeconds);
        boolean completed =
                (existing != null
                                && existing.getCompleted() != null
                                && existing.getCompleted() == 1)
                        || reachedThreshold(maxPositionSeconds, lesson.getDurationSec());

        LearningProgress progress = new LearningProgress();
        progress.setUserId(userId);
        progress.setCourseId(lesson.getCourseId());
        progress.setLessonId(lessonId);
        progress.setPositionSeconds(positionSeconds);
        progress.setMaxPositionSeconds(maxPositionSeconds);
        progress.setCompleted(completed ? 1 : 0);
        learningProgressMapper.merge(progress);
        return new LessonProgressView(
                lessonId, positionSeconds, maxPositionSeconds, completed, LocalDateTime.now());
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
        if (!entitlementService.hasEntitlement(userId, courseId)) {
            throw new BizException(ErrorCode.LEARN_NOT_ENTITLED);
        }
        List<LessonProgressView> lessons =
                learningProgressMapper.selectByUserCourse(userId, courseId).stream()
                        .map(
                                row ->
                                        new LessonProgressView(
                                                row.getLessonId(),
                                                row.getPositionSeconds(),
                                                row.getMaxPositionSeconds(),
                                                row.getCompleted() != null
                                                        && row.getCompleted() == 1,
                                                row.getUpdatedAt()))
                        .toList();
        return new CourseProgressView(courseId, lessons);
    }

    private static boolean reachedThreshold(int maxPositionSeconds, int durationSec) {
        return durationSec > 0
                && maxPositionSeconds * PERCENT_BASE >= (long) durationSec * COMPLETION_PERCENT;
    }
}
