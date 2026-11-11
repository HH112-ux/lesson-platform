package com.lp.course.upload;

import com.lp.course.config.LessonTranscodeProperties;
import com.lp.course.domain.LessonTranscodeStatus;
import com.lp.course.entity.LessonVideoTranscode;
import com.lp.course.mapper.LessonVideoTranscodeMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * Resumes lesson video transcode tasks left behind by a previous process.
 *
 * <p>Runs once when the application is ready. Rows still PENDING are resubmitted in case the
 * previous process crashed between inserting the row and handing it to the executor. A row left
 * RUNNING consumed the attempt it was on, so it is requeued only while attempts remain; otherwise
 * it becomes FAILED with an explicit reason and never stays stuck in RUNNING. PENDING is scanned
 * before any requeue so a row moved back to PENDING is submitted exactly once. A failure on one
 * task is logged and never stops the scan.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Component
public class LessonTranscodeRecovery {

    /** Failure reason stored when the interrupted run was the last allowed attempt. */
    static final String EXHAUSTED_REASON = "interrupted by restart, attempts exhausted";

    /** Failure reason stored when the executor refuses a resumed task. */
    static final String REJECTED_REASON = "transcode queue is full";

    private static final Logger LOG = LoggerFactory.getLogger(LessonTranscodeRecovery.class);

    private final LessonVideoTranscodeMapper mapper;

    private final LessonTranscodeWorker worker;

    private final LessonTranscodeProperties properties;

    /**
     * Creates the startup recovery.
     *
     * @param mapper task state access
     * @param worker executor receiving resumed tasks
     * @param properties enabled switch and attempt limit
     * @author Caspian
     * @since 0.11.0
     */
    public LessonTranscodeRecovery(
            LessonVideoTranscodeMapper mapper,
            LessonTranscodeWorker worker,
            LessonTranscodeProperties properties) {
        this.mapper = mapper;
        this.worker = worker;
        this.properties = properties;
    }

    /**
     * Requeues interrupted tasks and resubmits pending ones once the application is ready.
     *
     * @author Caspian
     * @since 0.11.0
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        if (!properties.enabled()) {
            LOG.info("lesson video transcode recovery skipped: disabled");
            return;
        }
        resubmitPending();
        recoverRunning();
    }

    private void recoverRunning() {
        List<Long> ids;
        try {
            ids = mapper.selectRunningIds();
        } catch (RuntimeException exception) {
            LOG.warn(
                    "lesson video transcode recovery skipped, cannot list running tasks",
                    exception);
            return;
        }
        LOG.info("lesson video transcode recovery found running tasks: count={}", ids.size());
        for (Long id : ids) {
            recoverOne(id);
        }
    }

    private void recoverOne(long id) {
        try {
            LessonVideoTranscode task = mapper.selectById(id);
            if (task == null
                    || task.getStatus() == null
                    || task.getStatus() != LessonTranscodeStatus.RUNNING) {
                return;
            }
            int attempts = task.getAttemptCount() == null ? 0 : task.getAttemptCount();
            if (attempts >= properties.maxAttempts()) {
                mapper.markFailed(id, EXHAUSTED_REASON, LocalDateTime.now());
                LOG.warn(
                        "lesson video transcode restart recovery failed task, attempts exhausted:"
                                + " taskId={} attempts={}",
                        id,
                        attempts);
                return;
            }
            if (mapper.requeueAfterRestart(id, attempts) == 0) {
                LOG.info(
                        "lesson video transcode restart recovery skipped, task changed: taskId={}",
                        id);
                return;
            }
            LOG.info(
                    "lesson video transcode restart recovery requeued: taskId={} attempts={}",
                    id,
                    attempts);
            submit(id);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "lesson video transcode restart recovery failed for task: taskId={}",
                    id,
                    exception);
        }
    }

    private void resubmitPending() {
        List<Long> ids;
        try {
            ids = mapper.selectPendingIds();
        } catch (RuntimeException exception) {
            LOG.warn(
                    "lesson video transcode recovery skipped, cannot list pending tasks",
                    exception);
            return;
        }
        LOG.info("lesson video transcode recovery found pending tasks: count={}", ids.size());
        for (Long id : ids) {
            submit(id);
        }
    }

    private void submit(long id) {
        try {
            worker.run(id);
        } catch (TaskRejectedException exception) {
            mapper.markFailed(id, REJECTED_REASON, LocalDateTime.now());
            LOG.warn("lesson video transcode recovery submission rejected: taskId={}", id);
        }
    }
}
