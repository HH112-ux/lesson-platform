package com.lp.course.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.money.Money;
import com.lp.course.config.CourseUploadProperties;
import com.lp.course.domain.LessonUploadStatus;
import com.lp.course.dto.TeacherChapterRequest;
import com.lp.course.dto.TeacherChapterView;
import com.lp.course.dto.TeacherCoursePageView;
import com.lp.course.dto.TeacherCourseRequest;
import com.lp.course.dto.TeacherCourseStatusCounts;
import com.lp.course.dto.TeacherCourseView;
import com.lp.course.dto.TeacherLessonRequest;
import com.lp.course.dto.TeacherLessonView;
import com.lp.course.dto.TeacherPlayUrlView;
import com.lp.course.dto.TeacherUploadSessionRequest;
import com.lp.course.dto.TeacherUploadSessionView;
import com.lp.course.entity.Course;
import com.lp.course.entity.CourseChapter;
import com.lp.course.entity.CourseLesson;
import com.lp.course.entity.CourseReviewAudit;
import com.lp.course.entity.LessonUpload;
import com.lp.course.entity.TeacherCourseStatusRow;
import com.lp.course.mapper.CourseReviewAuditMapper;
import com.lp.course.mapper.LessonUploadMapper;
import com.lp.course.mapper.TeacherCourseMapper;
import com.lp.course.service.TeacherCourseService;
import com.lp.course.upload.LessonPlaybackKeyResolver;
import com.lp.course.upload.LessonTranscodeService;
import com.lp.course.upload.LessonUploadObjectReader;
import com.lp.course.upload.LessonUploadObjectReader.DownloadedVideo;
import com.lp.course.upload.LessonUploadUrlSigner;
import com.lp.course.upload.LessonVideoMetadata;
import com.lp.course.upload.LessonVideoProbe;
import com.lp.course.upload.LessonVideoValidator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher console implementation backed by {@link TeacherCourseMapper}.
 *
 * <p>Editability follows the course state machine: only drafts and offline courses can change.
 * Editing an offline course returns it to draft in the same SQL statement, so the teacher must
 * submit it again and operations review the new content. Pending and published courses are read
 * only for the teacher.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class TeacherCourseServiceImpl implements TeacherCourseService {

    /** Draft status. */
    private static final int STATUS_DRAFT = 0;

    /** Status of a course waiting for an operations review decision. */
    private static final int STATUS_PENDING_REVIEW = 1;

    /** Status of a course taken offline by operations. */
    private static final int STATUS_OFFLINE = 3;

    /** Audit action code for a teacher withdrawal stored in {@code course_review_audit}. */
    private static final int ACTION_WITHDRAW = 3;

    /** Onboarding audit status of a teacher allowed to write courses. */
    private static final int AUDIT_APPROVED = 1;

    /** Maximum page size accepted by the console. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Maximum title length, matching {@code course.title}. */
    private static final int MAX_TITLE_LENGTH = 120;

    /** Maximum subtitle length, matching {@code course.subtitle}. */
    private static final int MAX_SUBTITLE_LENGTH = 200;

    /** Maximum cover URL length, matching {@code course.cover_url}. */
    private static final int MAX_COVER_LENGTH = 255;

    /** Content type fixed for the current upload path by {@code lesson_upload.content_type}. */
    private static final String UPLOAD_CONTENT_TYPE = "video/mp4";

    private final TeacherCourseMapper teacherCourseMapper;
    private final LessonUploadMapper lessonUploadMapper;
    private final CourseReviewAuditMapper courseReviewAuditMapper;
    private final LessonUploadUrlSigner uploadSigner;
    private final LessonUploadObjectReader uploadReader;
    private final CourseUploadProperties uploadProperties;
    private final LessonVideoProbe videoProbe;
    private final LessonVideoValidator videoValidator;
    private final LessonTranscodeService transcodeService;
    private final LessonPlaybackKeyResolver playbackKeyResolver;

    /**
     * Creates the service with its mapper, storage, probe and rule dependencies.
     *
     * @param teacherCourseMapper teacher scoped course statements
     * @param lessonUploadMapper upload session statements
     * @param courseReviewAuditMapper mapper appending course review audit entries
     * @param uploadSigner issues presigned PUT credentials
     * @param uploadReader stats, downloads and cleans stored objects
     * @param uploadProperties upload limits and credential lifetime
     * @param videoProbe reads video metadata with {@code ffprobe}
     * @param videoValidator applies the frozen video acceptance rules
     * @param transcodeService records the optional transcode task after a confirmation
     * @param playbackKeyResolver picks the key to sign, transcoded when READY, original otherwise
     * @author Caspian
     * @since 0.1.0
     */
    public TeacherCourseServiceImpl(
            TeacherCourseMapper teacherCourseMapper,
            LessonUploadMapper lessonUploadMapper,
            CourseReviewAuditMapper courseReviewAuditMapper,
            LessonUploadUrlSigner uploadSigner,
            LessonUploadObjectReader uploadReader,
            CourseUploadProperties uploadProperties,
            LessonVideoProbe videoProbe,
            LessonVideoValidator videoValidator,
            LessonTranscodeService transcodeService,
            LessonPlaybackKeyResolver playbackKeyResolver) {
        this.teacherCourseMapper = teacherCourseMapper;
        this.lessonUploadMapper = lessonUploadMapper;
        this.courseReviewAuditMapper = courseReviewAuditMapper;
        this.uploadSigner = uploadSigner;
        this.uploadReader = uploadReader;
        this.uploadProperties = uploadProperties;
        this.videoProbe = videoProbe;
        this.videoValidator = videoValidator;
        this.transcodeService = transcodeService;
        this.playbackKeyResolver = playbackKeyResolver;
    }

    /**
     * Lists one page of the caller's courses.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param status optional course status filter
     * @param page one based page number
     * @param size page size, capped at 100
     * @return one page of owned courses
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCoursePageView list(
            long userId, String roles, Integer status, int page, int size) {
        Actor actor = resolveActor(userId, roles);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (status != null && (status < STATUS_DRAFT || status > STATUS_OFFLINE)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status is out of range");
        }
        TeacherCourseStatusCounts counts =
                toCounts(teacherCourseMapper.countStatusByOwner(actor.teacherId()));
        long total = teacherCourseMapper.countByOwner(actor.teacherId(), status);
        if (total == 0) {
            return new TeacherCoursePageView(List.of(), 0, safePage, safeSize, counts);
        }
        List<TeacherCourseView> items =
                teacherCourseMapper
                        .selectPageByOwner(
                                actor.teacherId(),
                                status,
                                (long) (safePage - 1) * safeSize,
                                safeSize)
                        .stream()
                        .map(this::toView)
                        .toList();
        return new TeacherCoursePageView(items, total, safePage, safeSize, counts);
    }

    /**
     * Converts the aggregated row into the response counters.
     *
     * @param row aggregated counters, may be {@code null} when the query returns no row
     * @return counters with missing values treated as zero
     * @author Caspian
     * @since 0.2.0
     */
    private TeacherCourseStatusCounts toCounts(TeacherCourseStatusRow row) {
        if (row == null) {
            return TeacherCourseStatusCounts.empty();
        }
        return new TeacherCourseStatusCounts(
                zeroIfNull(row.getDraft()),
                zeroIfNull(row.getPending()),
                zeroIfNull(row.getPublished()),
                zeroIfNull(row.getOffline()));
    }

    /**
     * Returns zero for a missing counter.
     *
     * @param value counter read from the aggregate row
     * @return the value, or zero when {@code null}
     * @author Caspian
     * @since 0.2.0
     */
    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Creates a draft course owned by the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param request course form
     * @return the created course
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView create(long userId, String roles, TeacherCourseRequest request) {
        Actor actor = resolveActor(userId, roles);
        if (actor.teacherId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "teacher profile is required");
        }
        requireAuditedTeacher(actor);
        Course course = new Course();
        course.setOwnerTeacherId(actor.teacherId());
        apply(course, request);
        if (course.getCategoryId() == null
                || isBlank(course.getTitle())
                || isBlank(course.getCoverUrl())
                || course.getPrice() == null) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "category, title, cover and price are required");
        }
        teacherCourseMapper.insert(course);
        return toView(teacherCourseMapper.selectById(course.getId()));
    }

    /**
     * Edits a draft or offline course; editing an offline course sends it back to draft.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param request course form, omitted fields keep their current value
     * @return the course after the update
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView update(
            long userId, String roles, long courseId, TeacherCourseRequest request) {
        Actor actor = resolveActor(userId, roles);
        requireAuditedTeacher(actor);
        Course course = requireAccessible(actor, courseId);
        requireEditable(course);
        apply(course, request);
        if (teacherCourseMapper.updateEditable(course) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "course is no longer editable");
        }
        return toView(teacherCourseMapper.selectById(courseId));
    }

    /**
     * Loads one course visible to the caller.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return course detail
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView detail(long userId, String roles, long courseId) {
        return toView(requireAccessible(resolveActor(userId, roles), courseId));
    }

    /**
     * Moves a draft or offline course into the review queue.
     *
     * <p>A course is submittable only when it has at least one visible lesson and every visible
     * lesson carries a confirmed video, otherwise operations would review content that cannot be
     * played.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return the course in pending-review status
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherCourseView submit(long userId, String roles, long courseId) {
        Actor actor = resolveActor(userId, roles);
        Course course = requireAccessible(actor, courseId);
        requireEditable(course);
        long withVideo = teacherCourseMapper.countSubmittableLessons(courseId);
        if (withVideo == 0 || teacherCourseMapper.countLessonsMissingVideo(courseId) > 0) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "every visible lesson needs a confirmed video before submission");
        }
        if (teacherCourseMapper.submit(courseId, LocalDateTime.now()) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "course is no longer submittable");
        }
        return toView(teacherCourseMapper.selectById(courseId));
    }

    /**
     * Withdraws the caller's own pending submission and sends the course back to draft.
     *
     * <p>Ownership is checked against the teacher profile even for administrators, so an operations
     * user never withdraws on a teacher's behalf. The conditional update also keeps a concurrent
     * review decision from overwriting the withdrawal.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return the course back in draft status
     * @author Caspian
     * @since 0.13.0
     */
    @Override
    @Transactional
    public TeacherCourseView withdraw(long userId, String roles, long courseId) {
        Actor actor = resolveActor(userId, roles);
        Course course = requireAccessible(actor, courseId);
        if (!Objects.equals(course.getOwnerTeacherId(), actor.teacherId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "only the owning teacher can withdraw");
        }
        if (course.getStatus() != STATUS_PENDING_REVIEW) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, "only pending courses can be withdrawn");
        }
        LocalDateTime withdrawnAt = LocalDateTime.now();
        if (teacherCourseMapper.withdraw(courseId) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "course is no longer pending");
        }
        CourseReviewAudit audit = new CourseReviewAudit();
        audit.setCourseId(courseId);
        audit.setAction(ACTION_WITHDRAW);
        audit.setFromStatus(STATUS_PENDING_REVIEW);
        audit.setToStatus(STATUS_DRAFT);
        audit.setReviewerId(userId);
        audit.setCreatedAt(withdrawnAt);
        courseReviewAuditMapper.insert(audit);
        return toView(teacherCourseMapper.selectById(courseId));
    }

    /**
     * Lists the chapters and lessons of one owned course, including hidden rows.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return chapters ordered by sort
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<TeacherChapterView> chapters(long userId, String roles, long courseId) {
        Actor actor = resolveActor(userId, roles);
        requireAccessible(actor, courseId);
        Map<Long, List<TeacherLessonView>> lessonsByChapter =
                teacherCourseMapper.selectLessons(courseId).stream()
                        .map(this::toView)
                        .collect(
                                Collectors.groupingBy(
                                        TeacherLessonView::chapterId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        return teacherCourseMapper.selectChapters(courseId).stream()
                .map(
                        chapter ->
                                toView(
                                        chapter,
                                        lessonsByChapter.getOrDefault(chapter.getId(), List.of())))
                .toList();
    }

    /**
     * Creates a chapter in a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param request chapter form
     * @return the created chapter
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherChapterView createChapter(
            long userId, String roles, long courseId, TeacherChapterRequest request) {
        Course course = requireContentEditable(userId, roles, courseId);
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(courseId);
        chapter.setTitle(requireText(request.title(), "title", MAX_TITLE_LENGTH));
        chapter.setSort(requireSort(request.sort()));
        chapter.setStatus(requireFlag(request.status()));
        teacherCourseMapper.insertChapter(chapter);
        afterContentChange(course);
        return toView(chapter, List.of());
    }

    /**
     * Edits a chapter of a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param request chapter form, omitted fields keep their current value
     * @return the chapter after the update
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherChapterView updateChapter(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            TeacherChapterRequest request) {
        Course course = requireContentEditable(userId, roles, courseId);
        CourseChapter chapter = requireChapter(courseId, chapterId);
        if (request.title() != null) {
            chapter.setTitle(requireText(request.title(), "title", MAX_TITLE_LENGTH));
        }
        if (request.sort() != null) {
            chapter.setSort(requireSort(request.sort()));
        }
        if (request.status() != null) {
            chapter.setStatus(requireFlag(request.status()));
        }
        teacherCourseMapper.updateChapter(chapter);
        afterContentChange(course);
        List<TeacherLessonView> lessons =
                teacherCourseMapper.selectLessons(courseId).stream()
                        .filter(lesson -> lesson.getChapterId().equals(chapterId))
                        .map(this::toView)
                        .toList();
        return toView(chapter, lessons);
    }

    /**
     * Deletes a chapter and its lessons from a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(long userId, String roles, long courseId, long chapterId) {
        Course course = requireContentEditable(userId, roles, courseId);
        requireChapter(courseId, chapterId);
        teacherCourseMapper.deleteChapter(courseId, chapterId);
        teacherCourseMapper.deleteLessonsByChapter(courseId, chapterId);
        afterContentChange(course);
    }

    /**
     * Creates a lesson under a chapter of a draft or offline course.
     *
     * <p>The lesson starts without a video; the object key and duration are written by the upload
     * confirm endpoint, and requests carrying them are rejected.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param request lesson form
     * @return the created lesson
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherLessonView createLesson(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            TeacherLessonRequest request) {
        Course course = requireContentEditable(userId, roles, courseId);
        requireChapter(courseId, chapterId);
        rejectClientVideoFields(request);
        CourseLesson lesson = new CourseLesson();
        lesson.setCourseId(courseId);
        lesson.setChapterId(chapterId);
        lesson.setTitle(requireText(request.title(), "title", MAX_TITLE_LENGTH));
        // Video facts stay empty until the upload confirm endpoint writes them.
        lesson.setVideoUrl("");
        lesson.setVideoDurationSec(0);
        lesson.setIsFree(requireFreeFlag(request.isFree()));
        lesson.setSort(requireSort(request.sort()));
        lesson.setStatus(requireFlag(request.status()));
        teacherCourseMapper.insertLesson(lesson);
        afterContentChange(course);
        return toView(lesson);
    }

    /**
     * Edits a lesson of a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param request lesson form, omitted fields keep their current value
     * @return the lesson after the update
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public TeacherLessonView updateLesson(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            long lessonId,
            TeacherLessonRequest request) {
        Course course = requireContentEditable(userId, roles, courseId);
        requireChapter(courseId, chapterId);
        rejectClientVideoFields(request);
        CourseLesson lesson = teacherCourseMapper.selectLesson(courseId, chapterId, lessonId);
        if (lesson == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (request.title() != null) {
            lesson.setTitle(requireText(request.title(), "title", MAX_TITLE_LENGTH));
        }
        if (request.isFree() != null) {
            lesson.setIsFree(requireFreeFlag(request.isFree()));
        }
        if (request.sort() != null) {
            lesson.setSort(requireSort(request.sort()));
        }
        if (request.status() != null) {
            lesson.setStatus(requireFlag(request.status()));
        }
        teacherCourseMapper.updateLesson(lesson);
        afterContentChange(course);
        return toView(lesson);
    }

    /**
     * Deletes a lesson of a draft or offline course.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void deleteLesson(
            long userId, String roles, long courseId, long chapterId, long lessonId) {
        Course course = requireContentEditable(userId, roles, courseId);
        requireChapter(courseId, chapterId);
        if (teacherCourseMapper.deleteLesson(courseId, chapterId, lessonId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        afterContentChange(course);
    }

    /**
     * Creates or reuses the pending upload session of a lesson and signs a PUT credential.
     *
     * <p>A pending session already holding {@code uk_lesson_pending} is returned instead of
     * failing, and it keeps the size persisted on creation so the response never advertises a
     * declaration that confirmation would reject; an expired one is released and the insert retried
     * once.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param request declared file size, may be {@code null}
     * @return session and presigned PUT URL
     * @throws BizException {@code 40001} unusable size, {@code 61008} course not editable, {@code
     *     50206} storage unconfigured
     * @author Caspian
     * @since 0.7.0
     */
    @Override
    @Transactional
    public TeacherUploadSessionView createUploadSession(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            long lessonId,
            TeacherUploadSessionRequest request) {
        long size = requireUploadSize(request == null ? null : request.expectedSizeBytes());
        Course course = requireAccessible(resolveActor(userId, roles), courseId);
        if (course.getStatus() != STATUS_DRAFT && course.getStatus() != STATUS_OFFLINE) {
            throw new BizException(ErrorCode.LESSON_UPLOAD_STATE_INVALID);
        }
        if (teacherCourseMapper.selectLesson(courseId, chapterId, lessonId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        LessonUpload session = new LessonUpload();
        session.setCourseId(courseId);
        session.setChapterId(chapterId);
        session.setLessonId(lessonId);
        session.setUploaderId(userId);
        session.setObjectKey("lesson-videos/" + lessonId + "/pending");
        session.setContentType(UPLOAD_CONTENT_TYPE);
        session.setExpectedSizeBytes(size);
        session.setStatus(LessonUploadStatus.PENDING);
        session.setSessionExpiresAt(LocalDateTime.now().plus(uploadProperties.sessionTtl()));

        boolean inserted = insertUploadSession(session);
        if (inserted) {
            session.setObjectKey("lesson-videos/" + lessonId + "/" + session.getId() + ".mp4");
            lessonUploadMapper.updateObjectKey(session.getId(), session.getObjectKey());
        }
        Duration ttl =
                inserted
                        ? uploadProperties.sessionTtl()
                        : Duration.between(LocalDateTime.now(), session.getSessionExpiresAt());
        return new TeacherUploadSessionView(
                session.getId(),
                lessonId,
                session.getObjectKey(),
                uploadSigner.presignPut(session.getObjectKey(), ttl),
                session.getSessionExpiresAt(),
                session.getExpectedSizeBytes());
    }

    /**
     * Confirms an uploaded lesson video and writes the video facts to the lesson.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @param uploadId upload session primary key
     * @return the confirmed lesson, also returned when the confirmation is repeated
     * @author Caspian
     * @since 0.8.0
     */
    @Override
    public TeacherLessonView confirmUpload(
            long userId,
            String roles,
            long courseId,
            long chapterId,
            long lessonId,
            long uploadId) {
        Course course = requireAccessible(resolveActor(userId, roles), courseId);
        if (course.getStatus() != STATUS_DRAFT && course.getStatus() != STATUS_OFFLINE) {
            throw new BizException(ErrorCode.LESSON_UPLOAD_STATE_INVALID);
        }
        LessonUpload session = lessonUploadMapper.selectById(uploadId);
        if (session == null
                || session.getCourseId() != courseId
                || session.getChapterId() != chapterId
                || session.getLessonId() != lessonId) {
            throw new BizException(ErrorCode.LESSON_UPLOAD_NOT_FOUND);
        }
        if (session.getStatus() != LessonUploadStatus.CONFIRMED) {
            confirmVideo(session);
        }
        CourseLesson lesson = teacherCourseMapper.selectLesson(courseId, chapterId, lessonId);
        if (lesson == null) {
            throw new BizException(ErrorCode.LESSON_UPLOAD_NOT_FOUND);
        }
        transcodeService.enqueue(
                lesson.getId(),
                session.getId(),
                lesson.getVideoUrl(),
                lesson.getVideoSizeBytes() == null ? 0L : lesson.getVideoSizeBytes());
        return toView(lesson);
    }

    /**
     * Signs a short lived playback URL for a lesson video the caller may see.
     *
     * <p>Ownership is checked before signing; only a confirmed video is signable and the object key
     * never leaves the service.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @param lessonId lesson primary key
     * @return presigned GET URL with its lifetime and expiry
     * @throws BizException {@code 40301} when the caller does not own the course, {@code 40401}
     *     when the lesson is outside the course or has no confirmed video, {@code 50206} when
     *     storage is unconfigured
     * @author Caspian
     * @since 0.10.0
     */
    @Override
    public TeacherPlayUrlView playUrl(
            long userId, String roles, long courseId, long chapterId, long lessonId) {
        requireAccessible(resolveActor(userId, roles), courseId);
        CourseLesson lesson = teacherCourseMapper.selectLesson(courseId, chapterId, lessonId);
        if (lesson == null || lesson.getVideoUrl() == null || lesson.getVideoUrl().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "lesson has no confirmed video");
        }
        Duration ttl = uploadProperties.playUrlTtl();
        String playUrl =
                uploadSigner.presignGet(playbackKeyResolver.resolve(lesson.getVideoUrl()), ttl);
        return new TeacherPlayUrlView(
                playUrl, ttl.toSeconds(), Instant.now().plus(ttl).getEpochSecond());
    }

    /**
     * Checks the stored object and claims the session for the caller.
     *
     * <p>The size is compared against the declaration and the configured ceiling; the probe and the
     * acceptance rules run on the downloaded copy. The claim and the lesson columns are written by
     * one statement, so a concurrent caller matches no row and reads the winner state instead.
     *
     * @param session pending session whose object was uploaded
     * @author Caspian
     * @since 0.8.0
     */
    private void confirmVideo(LessonUpload session) {
        if (session.getStatus() != LessonUploadStatus.PENDING
                || !session.getSessionExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.LESSON_UPLOAD_NOT_FOUND);
        }
        long size = uploadReader.statSize(session.getObjectKey());
        if (size != session.getExpectedSizeBytes() || size > uploadProperties.maxSizeBytes()) {
            throw new BizException(
                    ErrorCode.LESSON_UPLOAD_VIDEO_INVALID,
                    "stored size does not match the declared size");
        }
        DownloadedVideo video = uploadReader.download(session.getObjectKey());
        try {
            LessonVideoMetadata metadata = videoProbe.probe(video.file());
            videoValidator.validate(metadata);
            int claimed =
                    lessonUploadMapper.confirm(
                            session.getId(),
                            LocalDateTime.now(),
                            session.getObjectKey(),
                            (int) Math.round(metadata.durationSeconds()),
                            metadata.videoCodec(),
                            metadata.width(),
                            metadata.height(),
                            size,
                            video.sha256());
            if (claimed == 0) {
                LessonUpload current = lessonUploadMapper.selectById(session.getId());
                if (current == null || current.getStatus() != LessonUploadStatus.CONFIRMED) {
                    throw new BizException(
                            ErrorCode.LESSON_UPLOAD_STATE_INVALID,
                            "confirmation is in progress, retry");
                }
            }
        } finally {
            uploadReader.delete(video);
        }
    }

    private long requireUploadSize(Long size) {
        long max = uploadProperties.maxSizeBytes();
        if (size == null || size <= 0 || size > max) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "expectedSizeBytes must be between 1 and " + max);
        }
        return size;
    }

    private boolean insertUploadSession(LessonUpload session) {
        try {
            lessonUploadMapper.insert(session);
            return true;
        } catch (DuplicateKeyException conflict) {
            LessonUpload existing =
                    lessonUploadMapper.selectPendingByLessonId(session.getLessonId());
            if (existing == null) {
                throw new BizException(ErrorCode.LESSON_UPLOAD_NOT_FOUND);
            }
            if (existing.getSessionExpiresAt().isAfter(LocalDateTime.now())) {
                session.setId(existing.getId());
                session.setObjectKey(existing.getObjectKey());
                session.setSessionExpiresAt(existing.getSessionExpiresAt());
                session.setExpectedSizeBytes(existing.getExpectedSizeBytes());
                return false;
            }
            lessonUploadMapper.markExpired(existing.getId());
            try {
                lessonUploadMapper.insert(session);
                return true;
            } catch (DuplicateKeyException retried) {
                throw new BizException(
                        ErrorCode.STATE_CONFLICT, "upload session is being renewed, retry later");
            }
        }
    }

    /**
     * Rejects course writes from a teacher whose onboarding review has not passed.
     *
     * <p>The check runs in the service layer so a controller or client cannot bypass it. It applies
     * to course creation and metadata editing; administrators are exempt because operations may
     * need to correct content before the teacher is approved.
     *
     * @param actor resolved caller
     * @throws BizException with {@code 61006} when the profile is pending or rejected
     * @author Caspian
     * @since 0.5.0
     */
    private void requireAuditedTeacher(Actor actor) {
        if (actor.admin() || actor.teacherId() == null) {
            return;
        }
        Integer auditStatus = teacherCourseMapper.selectAuditStatusByTeacherId(actor.teacherId());
        if (auditStatus == null || auditStatus != AUDIT_APPROVED) {
            throw new BizException(ErrorCode.COURSE_TEACHER_NOT_APPROVED);
        }
    }

    /**
     * Loads a course the caller may change content of.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @param courseId course primary key
     * @return editable course row
     * @author Caspian
     * @since 0.1.0
     */
    private Course requireContentEditable(long userId, String roles, long courseId) {
        Course course = requireAccessible(resolveActor(userId, roles), courseId);
        requireEditable(course);
        return course;
    }

    /**
     * Loads a chapter that belongs to the given course.
     *
     * @param courseId course primary key
     * @param chapterId chapter primary key
     * @return chapter row
     * @author Caspian
     * @since 0.1.0
     */
    private CourseChapter requireChapter(long courseId, long chapterId) {
        CourseChapter chapter = teacherCourseMapper.selectChapter(courseId, chapterId);
        if (chapter == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return chapter;
    }

    /**
     * Returns an offline course to draft after its content changed.
     *
     * @param course course row before the change
     * @author Caspian
     * @since 0.1.0
     */
    private void afterContentChange(Course course) {
        if (course.getStatus() == STATUS_OFFLINE) {
            teacherCourseMapper.resetOfflineToDraft(course.getId());
        }
    }

    /**
     * Validates a required text field.
     *
     * @param value text
     * @param field field name used in the error message
     * @param maxLength maximum accepted length
     * @return the trimmed text
     * @author Caspian
     * @since 0.1.0
     */
    private String requireText(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " is invalid");
        }
        return trimmed;
    }

    /**
     * Rejects video fields sent by the client.
     *
     * <p>The object key and duration are written only by the upload confirm endpoint; accepting
     * them here would let a teacher point {@code video_url} at any external address that the player
     * would later trust.
     *
     * @param request lesson form
     * @throws BizException with {@code 40001} when either video field is present
     * @author Caspian
     * @since 0.1.0
     */
    private void rejectClientVideoFields(TeacherLessonRequest request) {
        if (request.videoUrl() != null || request.durationSec() != null) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "videoUrl and durationSec are written by the upload confirmation endpoint");
        }
    }

    /**
     * Validates a free-trial flag.
     *
     * @param value flag, {@code null} defaults to paid
     * @return 0 or 1
     * @author Caspian
     * @since 0.1.0
     */
    private int requireFreeFlag(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value != 0 && value != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "isFree is out of range");
        }
        return value;
    }

    /**
     * Validates a visibility flag.
     *
     * @param value flag, {@code null} defaults to visible
     * @return 0 or 1
     * @author Caspian
     * @since 0.1.0
     */
    private int requireFlag(Integer value) {
        if (value == null) {
            return 1;
        }
        if (value != 0 && value != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status is out of range");
        }
        return value;
    }

    /**
     * Validates a display order.
     *
     * @param value order, {@code null} defaults to 0
     * @return the order
     * @author Caspian
     * @since 0.1.0
     */
    private int requireSort(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "sort must not be negative");
        }
        return value;
    }

    /**
     * Applies the non-null fields of the request onto the course row and validates each one.
     *
     * @param course target row, mutated in place
     * @param request submitted values
     * @author Caspian
     * @since 0.1.0
     */
    private void apply(Course course, TeacherCourseRequest request) {
        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "title length is invalid");
            }
            course.setTitle(title);
        }
        if (request.subtitle() != null) {
            String subtitle = request.subtitle().trim();
            if (subtitle.length() > MAX_SUBTITLE_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "subtitle is too long");
            }
            course.setSubtitle(subtitle.isEmpty() ? null : subtitle);
        }
        if (request.description() != null) {
            course.setDescription(request.description());
        }
        if (request.coverUrl() != null) {
            String coverUrl = request.coverUrl().trim();
            if (coverUrl.isEmpty() || coverUrl.length() > MAX_COVER_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "coverUrl length is invalid");
            }
            course.setCoverUrl(coverUrl);
        }
        if (request.categoryId() != null) {
            if (request.categoryId() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "categoryId must be positive");
            }
            course.setCategoryId(request.categoryId());
        }
        if (request.price() != null) {
            course.setPrice(requireMoney(request.price(), "price"));
        }
        if (Boolean.TRUE.equals(request.clearOriginalPrice())) {
            if (request.originalPrice() != null) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID, "cannot set and clear originalPrice at once");
            }
            course.setOriginalPrice(null);
        } else if (request.originalPrice() != null) {
            course.setOriginalPrice(requireMoney(request.originalPrice(), "originalPrice"));
        }
        if (request.level() != null) {
            if (request.level() < 1 || request.level() > 3) {
                throw new BizException(ErrorCode.PARAM_INVALID, "level is out of range");
            }
            course.setLevel(request.level());
        }
        if (course.getLevel() == null) {
            course.setLevel(1);
        }
    }

    /**
     * Validates a money amount: non-negative and with at most two decimals.
     *
     * @param value amount in yuan
     * @param field field name used in the error message
     * @return the amount unchanged
     * @author Caspian
     * @since 0.1.0
     */
    private BigDecimal requireMoney(BigDecimal value, String field) {
        try {
            Money.toFen(value);
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " is invalid");
        }
        return value;
    }

    /**
     * Resolves the caller into a teacher identity and admin flag.
     *
     * @param userId authenticated user id
     * @param roles comma separated gateway roles
     * @return resolved actor
     * @author Caspian
     * @since 0.1.0
     */
    private Actor resolveActor(long userId, String roles) {
        boolean admin = hasRole(roles, "ADMIN");
        if (!admin && !hasRole(roles, "TEACHER")) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Long teacherId = teacherCourseMapper.selectTeacherIdByUser(userId);
        if (teacherId == null && !admin) {
            throw new BizException(ErrorCode.FORBIDDEN, "teacher profile is required");
        }
        return new Actor(teacherId, admin);
    }

    /**
     * Loads a course and checks that the caller may see it.
     *
     * @param actor resolved caller
     * @param courseId course primary key
     * @return course row
     * @author Caspian
     * @since 0.1.0
     */
    private Course requireAccessible(Actor actor, long courseId) {
        Course course = teacherCourseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!actor.admin() && !Objects.equals(course.getOwnerTeacherId(), actor.teacherId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return course;
    }

    /**
     * Rejects changes to courses in a status the teacher cannot edit.
     *
     * @param course current row
     * @author Caspian
     * @since 0.1.0
     */
    private void requireEditable(Course course) {
        if (course.getStatus() != STATUS_DRAFT && course.getStatus() != STATUS_OFFLINE) {
            throw new BizException(
                    ErrorCode.STATE_CONFLICT, "only draft or offline courses are editable");
        }
    }

    /**
     * Checks whether a role list contains the given role.
     *
     * @param roles comma separated roles, may be {@code null}
     * @param expected role to look for
     * @return {@code true} when present
     * @author Caspian
     * @since 0.1.0
     */
    private boolean hasRole(String roles, String expected) {
        if (roles == null) {
            return false;
        }
        for (String role : roles.split(",")) {
            if (expected.equals(role.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a string is {@code null} or blank.
     *
     * @param value text
     * @return {@code true} when absent
     * @author Caspian
     * @since 0.1.0
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Maps a row to the console projection, converting yuan prices to fen.
     *
     * @param course course row
     * @return console view
     * @author Caspian
     * @since 0.1.0
     */
    private TeacherCourseView toView(Course course) {
        Long originalPriceFen =
                course.getOriginalPrice() == null ? null : Money.toFen(course.getOriginalPrice());
        return new TeacherCourseView(
                course.getId(),
                course.getTitle(),
                course.getSubtitle(),
                course.getCoverUrl(),
                course.getDescription(),
                Money.toFen(course.getPrice()),
                originalPriceFen,
                course.getCategoryId(),
                course.getLevel() == null ? 1 : course.getLevel(),
                course.getStatus(),
                course.getReviewRemark(),
                course.getLessonCount() == null ? 0L : course.getLessonCount(),
                course.getSubmittedAt(),
                course.getUpdatedAt());
    }

    /**
     * Maps a chapter row and its lessons to the console projection.
     *
     * @param chapter chapter row
     * @param lessons lessons of the chapter
     * @return console view
     * @author Caspian
     * @since 0.1.0
     */
    private TeacherChapterView toView(CourseChapter chapter, List<TeacherLessonView> lessons) {
        return new TeacherChapterView(
                chapter.getId(),
                chapter.getTitle(),
                chapter.getSort() == null ? 0 : chapter.getSort(),
                chapter.getStatus() == null ? 1 : chapter.getStatus(),
                lessons);
    }

    /**
     * Maps a lesson row to the console projection.
     *
     * @param lesson lesson row
     * @return console view
     * @author Caspian
     * @since 0.1.0
     */
    private TeacherLessonView toView(CourseLesson lesson) {
        return new TeacherLessonView(
                lesson.getId(),
                lesson.getChapterId() == null ? 0L : lesson.getChapterId(),
                lesson.getTitle(),
                lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank(),
                lesson.getVideoDurationSec() == null ? 0 : lesson.getVideoDurationSec(),
                lesson.getVideoCodec() == null ? "" : lesson.getVideoCodec(),
                lesson.getVideoWidth() == null ? 0 : lesson.getVideoWidth(),
                lesson.getVideoHeight() == null ? 0 : lesson.getVideoHeight(),
                lesson.getVideoSizeBytes() == null ? 0L : lesson.getVideoSizeBytes(),
                lesson.getIsFree() != null && lesson.getIsFree() == 1,
                lesson.getSort() == null ? 0 : lesson.getSort(),
                lesson.getStatus() == null ? 1 : lesson.getStatus());
    }

    /**
     * Resolved caller identity.
     *
     * @param teacherId {@code teacher_profile.id}, {@code null} for an administrator without one
     * @param admin whether the caller holds the {@code ADMIN} role
     * @author Caspian
     * @since 0.1.0
     */
    private record Actor(Long teacherId, boolean admin) {}
}
