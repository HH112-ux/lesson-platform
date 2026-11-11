package com.lp.course.cleanup;

/**
 * One pending cleanup row read by the worker.
 *
 * @param id row primary key
 * @param objectKey object key to delete from storage
 * @param attemptCount transient failures already recorded for this row
 * @author Caspian
 * @since 0.17.0
 */
public record LessonCleanupRow(long id, String objectKey, int attemptCount) {}
