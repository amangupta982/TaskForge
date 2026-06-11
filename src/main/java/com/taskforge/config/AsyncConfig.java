package com.taskforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the Spring @Async thread pool used by JobOrchestrator.executeAsync().
 *
 * System Design note:
 *   - corePoolSize  = 4  : always-alive threads for concurrent jobs
 *   - maxPoolSize   = 10 : burst capacity
 *   - queueCapacity = 50 : job queue before rejecting new submissions
 *
 * This is separate from the TaskExecutor's thread pool (which runs individual tasks).
 * This pool is for running JOBS asynchronously so the REST endpoint returns immediately.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("job-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
