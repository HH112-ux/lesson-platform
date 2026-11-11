package com.lp.course.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the lesson content cleanup worker.
 *
 * <p>Every value has a default so the worker is usable without configuration, but each one is a
 * real configuration key: the batch size and the retry budget decide how fast a backlog drains and
 * how long a transient storage outage is tolerated.
 *
 * @param enabled whether the scheduler runs the worker
 * @param batchSize rows handled per tick, small enough to keep one tick short
 * @param maxAttempts transient failures tolerated before a row becomes terminal
 * @param baseBackoff delay before the second attempt; doubles per attempt
 * @param maxBackoff cap of a single backoff step
 * @param deferDelay re-check delay of an object that is still referenced
 * @param staleAfter age after which a {@code RUNNING} row is treated as a crashed tick
 * @author Caspian
 * @since 0.17.0
 */
@ConfigurationProperties(prefix = "lp.course.cleanup")
public record LessonCleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int batchSize,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("30s") Duration baseBackoff,
        @DefaultValue("10m") Duration maxBackoff,
        @DefaultValue("10m") Duration deferDelay,
        @DefaultValue("10m") Duration staleAfter) {}
