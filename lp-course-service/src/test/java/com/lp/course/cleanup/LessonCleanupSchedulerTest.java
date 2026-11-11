package com.lp.course.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Verifies the switch removes the ticker and that a failing tick never escapes to the scheduler.
 *
 * @author Caspian
 * @since 0.17.0
 */
class LessonCleanupSchedulerTest {

    private final LessonContentCleanupWorker worker = mock(LessonContentCleanupWorker.class);

    private final LessonCleanupMetrics metrics = mock(LessonCleanupMetrics.class);

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(LessonContentCleanupWorker.class, () -> worker)
                    .withBean(LessonCleanupMetrics.class, () -> metrics)
                    .withUserConfiguration(LessonCleanupScheduler.class);

    /**
     * The default is on, so a deployment that sets nothing still drains the queue.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void isPresentByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(LessonCleanupScheduler.class));
    }

    /**
     * Turning the switch off removes the bean, which stops the ticks without touching the queue.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void isAbsentWhenTheSwitchIsOff() {
        runner.withPropertyValues("lp.course.cleanup.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LessonCleanupScheduler.class));
    }

    /**
     * An exception thrown by the queue itself is logged, not rethrown, so ticks keep coming.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void swallowsTickFailuresAndStillRefreshesGauges() {
        when(worker.tick()).thenThrow(new IllegalStateException("database is down"));

        new LessonCleanupScheduler(worker, metrics).run();

        verify(metrics).refresh();
    }

    /**
     * A healthy tick runs the worker and refreshes the gauges afterwards.
     *
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void runsTheWorkerAndRefreshesGauges() {
        when(worker.tick()).thenReturn(3);

        new LessonCleanupScheduler(worker, metrics).run();

        verify(worker).tick();
        verify(metrics).refresh();
    }

    /**
     * {@code fixedDelay} (not {@code fixedRate}) is what keeps a slow tick from overlapping the
     * next one, so the annotation is asserted rather than assumed.
     *
     * @throws NoSuchMethodException if the scheduled method is renamed without updating this test
     * @author Caspian
     * @since 0.17.0
     */
    @Test
    void schedulesWithFixedDelaySoTicksCannotOverlap() throws NoSuchMethodException {
        Scheduled scheduled =
                LessonCleanupScheduler.class.getMethod("run").getAnnotation(Scheduled.class);

        assertEquals(30_000, scheduled.fixedDelay());
        assertTrue(scheduled.fixedDelayString().isEmpty());
        assertEquals(-1, scheduled.fixedRate());
    }
}
