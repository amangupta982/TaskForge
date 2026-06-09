package com.taskforge.scheduler;

import com.taskforge.model.Task;
import com.taskforge.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * RetryManager — Exponential Backoff for Failed Tasks
 *
 * DSA / System Design Concept:
 *   Exponential backoff prevents "thundering herd" — the situation where
 *   many tasks fail simultaneously and all immediately retry, overwhelming
 *   shared resources.
 *
 *   Backoff formula: delay = 2^attemptCount * 1000 ms
 *     Attempt 1: wait 2s before retry
 *     Attempt 2: wait 4s before retry
 *     Attempt 3: wait 8s before retry
 *     Attempt 4+: DEAD (max retries exhausted)
 *
 * Implementation:
 *   Uses ScheduledExecutorService.schedule() to re-enqueue the task
 *   after the computed delay. Non-blocking — the calling thread returns
 *   immediately and the retry happens asynchronously.
 *
 * Real-world analogy:
 *   AWS SQS visibility timeout, Kafka consumer retry, HTTP 429 retry-after.
 */
@Component
public class RetryManager {

    private static final Logger log = LoggerFactory.getLogger(RetryManager.class);

    @Autowired
    private PriorityTaskScheduler scheduler;

    // Single-thread scheduler for delayed retry submissions
    private final ScheduledExecutorService retryScheduler =
            Executors.newScheduledThreadPool(2);

    /**
     * Schedules a failed task for retry after exponential backoff delay.
     * If max retries exhausted, marks task as DEAD and does not re-enqueue.
     *
     * @param task     the failed task
     * @param onRetry  callback to invoke when the task is re-enqueued
     */
    public void scheduleRetry(Task task, Runnable onRetry) {
        if (task.getStatus() == TaskStatus.DEAD) {
            log.warn("Task '{}' has exhausted all {} retries. Marking DEAD.",
                    task.getName(), task.getMaxRetries());
            return;
        }

        long delayMs = task.backoffDelayMs();
        int attempt = task.getAttemptCount();

        log.info("Scheduling retry #{} for task '{}' in {}ms (exponential backoff)",
                attempt, task.getName(), delayMs);

        retryScheduler.schedule(() -> {
            log.info("Retrying task '{}' (attempt #{})...", task.getName(), attempt);
            scheduler.enqueue(task);
            if (onRetry != null) {
                onRetry.run();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Shuts down the retry scheduler gracefully.
     * Waits up to 5 seconds for pending retries to be submitted.
     */
    public void shutdown() {
        retryScheduler.shutdown();
        try {
            if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
