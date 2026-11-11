package com.lp.course.upload;

import com.lp.course.entity.CourseLesson;
import com.lp.course.entity.LessonUpload;
import com.lp.course.mapper.LessonContentCleanupMapper;
import com.lp.course.mapper.LessonUploadMapper;
import com.lp.course.mapper.TeacherCourseMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims an upload session and writes the lesson video facts in one short transaction.
 *
 * <p>The caller verifies the stored object first, so no network call runs inside the transaction.
 * The lesson is read before the claim because the claim overwrites {@code video_url}; a winning
 * claim over a different non-blank key registers it here, and a failed registration rolls back.
 *
 * @author Caspian
 * @since 0.16.0
 */
@Service
public class LessonUploadConfirmWriter {

    private final TeacherCourseMapper teacherCourseMapper;
    private final LessonUploadMapper lessonUploadMapper;
    private final LessonContentCleanupMapper contentCleanupMapper;

    /**
     * Creates the writer with the mappers owning the claim and the cleanup registry.
     *
     * @param teacherCourseMapper reads the lesson key that the claim overwrites
     * @param lessonUploadMapper claims the session and writes the lesson video facts
     * @param contentCleanupMapper registers the overwritten object key
     * @author Caspian
     * @since 0.16.0
     */
    public LessonUploadConfirmWriter(
            TeacherCourseMapper teacherCourseMapper,
            LessonUploadMapper lessonUploadMapper,
            LessonContentCleanupMapper contentCleanupMapper) {
        this.teacherCourseMapper = teacherCourseMapper;
        this.lessonUploadMapper = lessonUploadMapper;
        this.contentCleanupMapper = contentCleanupMapper;
    }

    /**
     * Claims the session and registers the object key it replaces.
     *
     * <p>The read precedes the claim, so only a winning claim over a different non-blank key
     * registers: a first upload has nothing to clean and a concurrent loser must not register.
     *
     * @param session pending session whose object passed the storage checks
     * @param metadata facts probed from the uploaded object
     * @param sizeBytes stored object size in bytes
     * @param sha256 lower case hex digest of the stored object
     * @return affected rows, {@code 0} when another caller already claimed the session or the
     *     lesson is gone
     * @author Caspian
     * @since 0.16.0
     */
    @Transactional(rollbackFor = Exception.class)
    public int confirm(
            LessonUpload session, LessonVideoMetadata metadata, long sizeBytes, String sha256) {
        CourseLesson lesson =
                teacherCourseMapper.selectLesson(
                        session.getCourseId(), session.getChapterId(), session.getLessonId());
        String previousKey = lesson == null ? null : lesson.getVideoUrl();
        int claimed =
                lessonUploadMapper.confirm(
                        session.getId(),
                        LocalDateTime.now(),
                        session.getObjectKey(),
                        (int) Math.round(metadata.durationSeconds()),
                        metadata.videoCodec(),
                        metadata.width(),
                        metadata.height(),
                        sizeBytes,
                        sha256);
        if (claimed == 1
                && previousKey != null
                && !previousKey.isBlank()
                && !previousKey.equals(session.getObjectKey())) {
            contentCleanupMapper.insertObjectKeyCleanup(session.getCourseId(), previousKey);
        }
        return claimed;
    }
}
