package com.lp.course.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Provides the bounded executor used by lesson video transcode workers.
 *
 * <p>The pool size and queue capacity come from configuration. The executor rejects work when the
 * queue is full instead of growing without limit or running on the caller thread; the enqueue
 * service turns that rejection into a failed task with a recorded reason.
 *
 * @author Caspian
 * @since 0.11.0
 */
@Configuration
@EnableAsync
public class LessonTranscodeAsyncConfig {

    /**
     * Builds the transcode executor.
     *
     * @param properties worker count, queue capacity and retry settings
     * @return initialized executor named {@code lessonTranscodeExecutor}
     * @author Caspian
     * @since 0.11.0
     */
    @Bean(name = "lessonTranscodeExecutor")
    public Executor lessonTranscodeExecutor(LessonTranscodeProperties properties) {
        int concurrency = Math.max(1, properties.concurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(Math.max(1, properties.queueCapacity()));
        executor.setThreadNamePrefix("lesson-transcode-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
