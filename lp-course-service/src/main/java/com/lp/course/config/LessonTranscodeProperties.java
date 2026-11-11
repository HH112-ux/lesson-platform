package com.lp.course.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the asynchronous lesson video transcode pipeline.
 *
 * <p>The worker count is deliberately small: transcoding is CPU bound, so more parallel jobs on one
 * instance would only lengthen every job. The queue is bounded, and the caller records a failed
 * task when it is full instead of blocking the upload confirmation request.
 *
 * @param enabled whether confirmations queue a transcode task
 * @param concurrency number of worker threads, at least one
 * @param queueCapacity how many tasks may wait for a worker
 * @param ffmpegPath absolute path or executable name of {@code ffmpeg}
 * @param timeout wall clock limit of one {@code ffmpeg} run
 * @param maxAttempts attempts before a task is marked failed, three by default
 * @param retryBackoff pause between two attempts
 * @param downloadTimeout wall clock limit of reading the source object from storage
 * @param uploadTimeout wall clock limit of writing the transcoded object to storage
 * @author Caspian
 * @since 0.11.0
 */
@ConfigurationProperties(prefix = "lp.course.transcode")
public record LessonTranscodeProperties(
        boolean enabled,
        int concurrency,
        int queueCapacity,
        String ffmpegPath,
        Duration timeout,
        int maxAttempts,
        Duration retryBackoff,
        Duration downloadTimeout,
        Duration uploadTimeout) {}
