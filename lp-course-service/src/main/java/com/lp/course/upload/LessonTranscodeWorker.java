package com.lp.course.upload;

import com.lp.course.config.LessonTranscodeProperties;
import com.lp.course.domain.LessonTranscodeStatus;
import com.lp.course.entity.LessonVideoTranscode;
import com.lp.course.mapper.LessonVideoTranscodeMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs one lesson video transcode task with bounded retries.
 *
 * <p>Every attempt first moves the row to RUNNING through a conditional update, so a task that
 * became READY or FAILED is never restarted. An interrupted run leaves the row RUNNING on purpose:
 * shutdown does not consume the remaining attempts, and the startup recovery scan requeues the row,
 * after which this worker resumes from the recorded attempt number. Failed attempts increment
 * {@code lp.course.transcode.failed} and are logged with the {@code ffmpeg} stderr tail; a task
 * that exhausts its attempts becomes FAILED with the last reason persisted.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Component
public class LessonTranscodeWorker {

    /** Metric counting failed transcode attempts. */
    static final String FAILURE_METRIC = "lp.course.transcode.failed";

    /** Max characters stored in {@code failure_reason}. */
    static final int MAX_REASON_LENGTH = 255;

    private static final Logger LOG = LoggerFactory.getLogger(LessonTranscodeWorker.class);

    private final LessonVideoTranscodeMapper mapper;

    private final LessonTranscodeObjectStore objectStore;

    private final LessonVideoTranscoder transcoder;

    private final LessonTranscodeProperties properties;

    private final Counter failures;

    /**
     * Creates the worker and its failure counter.
     *
     * @param mapper task state access
     * @param objectStore source download and output upload
     * @param transcoder ffmpeg invocation
     * @param properties attempt limit and backoff
     * @param meterRegistry registry receiving the failure counter
     * @author Caspian
     * @since 0.11.0
     */
    public LessonTranscodeWorker(
            LessonVideoTranscodeMapper mapper,
            LessonTranscodeObjectStore objectStore,
            LessonVideoTranscoder transcoder,
            LessonTranscodeProperties properties,
            MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.objectStore = objectStore;
        this.transcoder = transcoder;
        this.properties = properties;
        this.failures =
                Counter.builder(FAILURE_METRIC)
                        .description("Lesson video transcode attempts that failed")
                        .register(meterRegistry);
    }

    /**
     * Transcodes one task on the bounded executor.
     *
     * @param taskId primary key of the task to run
     * @author Caspian
     * @since 0.11.0
     */
    @Async("lessonTranscodeExecutor")
    public void run(long taskId) {
        LessonVideoTranscode task = mapper.selectById(taskId);
        if (task == null
                || task.getStatus() == null
                || task.getStatus() == LessonTranscodeStatus.READY) {
            return;
        }
        int firstAttempt = (task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1;
        if (firstAttempt > properties.maxAttempts()) {
            mapper.markFailed(taskId, "attempt limit already reached", LocalDateTime.now());
            LOG.warn("lesson video transcode skipped, attempts exhausted: taskId={}", taskId);
            return;
        }
        Path source = null;
        String lastReason = null;
        int lastAttempt = firstAttempt - 1;
        boolean interrupted = false;
        try {
            for (int attempt = firstAttempt; attempt <= properties.maxAttempts(); attempt++) {
                lastAttempt = attempt;
                if (mapper.markRunning(taskId, attempt, LocalDateTime.now()) == 0) {
                    LOG.info(
                            "lesson video transcode skipped, task is not runnable: taskId={}",
                            taskId);
                    return;
                }
                Path output = null;
                try {
                    if (source == null) {
                        source = objectStore.download(task.getSourceObjectKey());
                    }
                    output = Files.createTempFile(source.getParent(), "transcoded-", ".mp4");
                    transcoder.transcode(source, output);
                    String outputKey = objectStore.outputKey(task.getSourceObjectKey());
                    objectStore.upload(outputKey, output);
                    if (mapper.markReady(taskId, outputKey, LocalDateTime.now()) == 0) {
                        LOG.warn(
                                "lesson video transcode finished but task was no longer running:"
                                        + " taskId={}",
                                taskId);
                    } else {
                        LOG.info(
                                "lesson video transcoded: taskId={} lessonId={} attempt={}",
                                taskId,
                                task.getLessonId(),
                                attempt);
                    }
                    return;
                } catch (Exception exception) {
                    lastReason = reason(exception);
                    failures.increment();
                    LOG.warn(
                            "lesson video transcode attempt failed: taskId={} attempt={} reason={}",
                            taskId,
                            attempt,
                            lastReason);
                    boolean stop =
                            Thread.currentThread().isInterrupted()
                                    || attempt >= properties.maxAttempts()
                                    || !backoff();
                    interrupted = Thread.currentThread().isInterrupted();
                    if (stop) {
                        break;
                    }
                } finally {
                    deleteQuietly(output);
                }
            }
            if (interrupted) {
                LOG.info(
                        "lesson video transcode interrupted, left RUNNING for restart recovery:"
                                + " taskId={}",
                        taskId);
                return;
            }
            mapper.markFailed(
                    taskId,
                    lastReason == null ? "transcode did not run" : lastReason,
                    LocalDateTime.now());
            LOG.error(
                    "lesson video transcode failed: taskId={} attempts={} reason={}",
                    taskId,
                    lastAttempt,
                    lastReason);
        } finally {
            if (source != null) {
                objectStore.delete(source.getParent());
            }
        }
    }

    private boolean backoff() {
        long millis = properties.retryBackoff() == null ? 0 : properties.retryBackoff().toMillis();
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String reason(Exception exception) {
        String message = exception.getMessage();
        String reason =
                message == null || message.isBlank()
                        ? exception.getClass().getSimpleName()
                        : message;
        return reason.length() <= MAX_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_REASON_LENGTH);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // The temporary directory is removed with the source file.
        }
    }
}
