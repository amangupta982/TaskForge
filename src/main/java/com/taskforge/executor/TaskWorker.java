package com.taskforge.executor;

import com.taskforge.model.Task;
import com.taskforge.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * TaskWorker — Runnable wrapper for a single Task.
 *
 * Each TaskWorker is submitted to the ThreadPoolExecutor as a Runnable.
 * It:
 *   1. Marks the task as RUNNING
 *   2. Simulates work (sleeps for task.getDurationMs())
 *   3. On success: invokes onSuccess callback (triggers dependency resolution)
 *   4. On failure: invokes onFailure callback (triggers retry logic)
 *
 * In production, step 2 would call real business logic (DB queries, API calls,
 * data processing, etc.) — the simulation makes it testable without external deps.
 *
 * Thread Safety:
 *   Each TaskWorker instance is only ever run by ONE thread.
 *   The callbacks are responsible for thread-safe state updates.
 */
public class TaskWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    private final Task task;
    private final Consumer<Task> onSuccess;  // called when task completes
    private final Consumer<Task> onFailure;  // called when task throws exception

    // Inject a failure rate for testing retry logic (0.0 = never fail, 1.0 = always fail)
    private final double simulatedFailureRate;

    public TaskWorker(Task task, Consumer<Task> onSuccess,
                      Consumer<Task> onFailure, double simulatedFailureRate) {
        this.task = task;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
        this.simulatedFailureRate = simulatedFailureRate;
    }

    public TaskWorker(Task task, Consumer<Task> onSuccess, Consumer<Task> onFailure) {
        this(task, onSuccess, onFailure, 0.0);
    }

    @Override
    public void run() {
        log.info("Starting task '{}' [priority={}] on thread {}",
                task.getName(), task.getPriority(), Thread.currentThread().getName());

        task.markRunning();

        try {
            // Simulate task execution time
            simulateWork();

            // Mark successful
            task.markDone();
            log.info("Completed task '{}' successfully", task.getName());
            onSuccess.accept(task);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.markFailed("Task interrupted: " + e.getMessage());
            log.warn("Task '{}' was interrupted", task.getName());
            onFailure.accept(task);

        } catch (Exception e) {
            task.markFailed(e.getMessage());
            log.error("Task '{}' failed: {}", task.getName(), e.getMessage());
            onFailure.accept(task);
        }
    }

    /**
     * Simulates work by sleeping for the task's configured duration.
     * Randomly throws an exception based on simulatedFailureRate to test retry logic.
     */
    private void simulateWork() throws InterruptedException {
        // Simulate actual processing time
        Thread.sleep(task.getDurationMs());

        // Simulate random failures for testing retry + backoff
        if (simulatedFailureRate > 0 && Math.random() < simulatedFailureRate) {
            throw new RuntimeException(
                String.format("Simulated failure in task '%s' (failure rate=%.1f%%)",
                    task.getName(), simulatedFailureRate * 100));
        }
    }

    public Task getTask() { return task; }
}
