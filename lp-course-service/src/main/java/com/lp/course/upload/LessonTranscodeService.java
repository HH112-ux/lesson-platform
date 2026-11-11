package com.lp.course.upload;

import com.lp.course.config.LessonTranscodeProperties;
import com.lp.course.domain.LessonTranscodeStatus;
import com.lp.course.entity.LessonVideoTranscode;
import com.lp.course.mapper.LessonVideoTranscodeMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/**
 * Records and submits a transcode task after a lesson video is confirmed.
 *
 * <p>Enqueue is best effort on purpose: the confirmation has already validated and stored a
 * playable original, so a database hiccup or a full executor queue must never fail the
 * confirmation. Problems are counted with {@code lp.course.transcode.enqueue_failed} and logged.
 * The unique source object key makes repeated confirmations idempotent: an existing row is returned
 * instead of queued again.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Service
public class LessonTranscodeService {

    /** Metric counting confirmations whose task could not be recorded or submitted. */
    static final String ENQUEUE_FAILURE_METRIC = "lp.course.transcode.enqueue_failed";

    /** Failure reason stored when the executor refuses a newly recorded task. */
    static final String REJECTED_REASON = "transcode queue is full";

    private static final Logger LOG = LoggerFactory.getLogger(LessonTranscodeService.class);

    private final LessonVideoTranscodeMapper mapper;

    private final LessonTranscodeWorker worker;

    private final LessonTranscodeProperties properties;

    private final Counter enqueueFailures;

    /**
     * Creates the enqueue service and its failure counter.
     *
     * @param mapper task state access
     * @param worker executor receiving the task
     * @param properties enabled switch
     * @param meterRegistry registry receiving the enqueue failure counter
     * @author Caspian
     * @since 0.11.0
     */
    public LessonTranscodeService(
            LessonVideoTranscodeMapper mapper,
            LessonTranscodeWorker worker,
            LessonTranscodeProperties properties,
            MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.worker = worker;
        this.properties = properties;
        this.enqueueFailures =
                Counter.builder(ENQUEUE_FAILURE_METRIC)
                        .description("Lesson video transcode tasks that could not be enqueued")
                        .register(meterRegistry);
    }

    /**
     * Records one task for a confirmed source object and submits it to the executor.
     *
     * <p>Only the insert and the asynchronous hand off happen here, so the confirmation is not
     * slowed down. An existing task for the same source object is returned untouched, which keeps
     * repeated confirmations idempotent. This method never throws.
     *
     * @param lessonId lesson owning the video
     * @param uploadId confirmed upload session
     * @param sourceObjectKey stored source object key
     * @param sourceSizeBytes stored source size
     * @return the inserted or already existing task, {@code null} when the task could not be
     *     recorded
     * @author Caspian
     * @since 0.11.0
     */
    public LessonVideoTranscode enqueue(
            long lessonId, long uploadId, String sourceObjectKey, long sourceSizeBytes) {
        if (!properties.enabled() || sourceObjectKey == null || sourceObjectKey.isBlank()) {
            return null;
        }
        try {
            LessonVideoTranscode task = new LessonVideoTranscode();
            task.setLessonId(lessonId);
            task.setUploadId(uploadId);
            task.setSourceObjectKey(sourceObjectKey);
            task.setSourceSizeBytes(sourceSizeBytes);
            task.setStatus(LessonTranscodeStatus.PENDING);
            if (mapper.insertIfAbsent(task) == 0) {
                LessonVideoTranscode existing = mapper.selectBySourceObjectKey(sourceObjectKey);
                LOG.info(
                        "lesson video transcode already recorded: lessonId={} status={}",
                        lessonId,
                        existing == null ? null : existing.getStatus());
                return existing;
            }
            submit(task.getId());
            return task;
        } catch (RuntimeException exception) {
            enqueueFailures.increment();
            LOG.warn(
                    "lesson video transcode enqueue failed, confirmation is unaffected:"
                            + " lessonId={} source={}",
                    lessonId,
                    sourceObjectKey,
                    exception);
            return null;
        }
    }

    private void submit(long taskId) {
        try {
            worker.run(taskId);
        } catch (TaskRejectedException exception) {
            enqueueFailures.increment();
            LOG.warn("lesson video transcode submission rejected: taskId={}", taskId);
            markRejected(taskId);
        }
    }

    private void markRejected(long taskId) {
        try {
            mapper.markFailed(taskId, REJECTED_REASON, LocalDateTime.now());
        } catch (RuntimeException exception) {
            LOG.warn(
                    "lesson video transcode rejected task could not be closed: taskId={}",
                    taskId,
                    exception);
        }
    }
}
