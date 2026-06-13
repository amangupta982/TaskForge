package com.taskforge.executor;

import com.taskforge.model.Task;
import com.taskforge.model.TaskStatus;
import com.taskforge.scheduler.DependencyTracker;
import com.taskforge.scheduler.PriorityTaskScheduler;
import com.taskforge.scheduler.RetryManager;
import com.taskforge.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * TaskExecutor — Thread Pool + Execution Orchestration
 *
 * System Design Concept:
 *   Wraps Java's ThreadPoolExecutor to run tasks in parallel.
 *   The pool size = number of CPU cores (configurable).
 *
 *   Execution loop:
 *   1. Find all tasks with in-degree 0 (no dependencies)
 *   2. Wrap each in TaskWorker (a Runnable), submit to ThreadPoolExecutor
 *   3. On task success: DependencyTracker finds newly-ready successors → enqueue them
 *   4. On task failure: RetryManager schedules retry with exponential backoff
 *   5. Repeat until CountDownLatch reaches 0 (all tasks terminal)
 *
 * Fix applied:
 *   onSuccess was referenced inside its own definition (circular lambda).
 *   Solved by extracting submitTask() method so onSuccess is fully defined
 *   before being passed anywhere.
 */
@Component
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private static final int THREAD_POOL_SIZE =
            Runtime.getRuntime().availableProcessors();

    @Autowired private PriorityTaskScheduler scheduler;
    @Autowired private DependencyTracker dependencyTracker;
    @Autowired private RetryManager retryManager;

    // Keep references so callbacks can use them
    private ExecutorService threadPool;
    private CountDownLatch latch;
    private Job currentJob;
    private double currentFailureRate;

    /**
     * Executes an entire Job by running tasks in DAG order.
     * Blocks the calling thread until all tasks reach a terminal state (DONE or DEAD).
     */
    public void execute(Job job, double simulatedFailureRate) throws InterruptedException {
        int totalTasks = job.getTasks().size();
        log.info("Starting execution of Job '{}' with {} tasks on {} threads",
                job.getName(), totalTasks, THREAD_POOL_SIZE);

        // Store as instance fields so all helper methods can access them
        this.currentJob = job;
        this.currentFailureRate = simulatedFailureRate;
        this.latch = new CountDownLatch(totalTasks);
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("taskforge-worker-" + t.getId());
                    t.setDaemon(true);
                    return t;
                });

        // Initialize dependency tracker with this job's DAG
        dependencyTracker.initialize(job.getDag());

        // Submit all initially-ready tasks (in-degree = 0)
        List<String> initialReady = dependencyTracker.getInitialReadyTasks();
        log.info("Initial ready tasks (in-degree=0): {}", initialReady);

        for (String taskId : initialReady) {
            Task task = job.getTask(taskId);
            if (task != null) {
                submitTask(task);
            }
        }

        // Block until all tasks finish (or timeout after 10 minutes)
        boolean finished = latch.await(10, TimeUnit.MINUTES);
        threadPool.shutdown();

        if (!finished) {
            log.error("Job '{}' timed out after 10 minutes.", job.getName());
            job.markFailed("Execution timed out after 10 minutes.");
        } else if (job.hasDeadTask()) {
            job.markFailed("One or more tasks permanently failed.");
        }
    }

    /**
     * Submits a single task to the thread pool.
     * Defines success and failure callbacks cleanly — no circular lambda references.
     */
    private void submitTask(Task task) {
        Consumer<Task> onSuccess = this::handleSuccess;
        Consumer<Task> onFailure = this::handleFailure;

        scheduler.enqueue(task);
        threadPool.submit(new TaskWorker(task, onSuccess, onFailure, currentFailureRate));
    }

    /**
     * Called when a task completes successfully.
     * Finds newly-unblocked successors and submits them.
     */
    private void handleSuccess(Task completedTask) {
        latch.countDown();
        log.debug("Task '{}' DONE. Remaining: {}", completedTask.getName(), latch.getCount());

        // Find successors whose dependency count just hit 0
        List<String> newlyReady = dependencyTracker.onTaskCompleted(completedTask.getId());

        for (String readyId : newlyReady) {
            Task readyTask = currentJob.getTask(readyId);
            if (readyTask != null) {
                log.info("Task '{}' is now READY (unblocked by '{}')",
                        readyTask.getName(), completedTask.getName());
                submitTask(readyTask);
            }
        }

        // Check if job is fully complete
        if (currentJob.allTasksDone()) {
            currentJob.markCompleted();
            log.info("Job '{}' COMPLETED successfully!", currentJob.getName());
        }
    }

    /**
     * Called when a task fails.
     * Schedules a retry with exponential backoff, or counts down if DEAD.
     */
    private void handleFailure(Task failedTask) {
        if (failedTask.canRetry()) {
            // Schedule retry — re-submits after 2^n seconds delay
            retryManager.scheduleRetry(failedTask, () -> {
                log.info("Re-submitting task '{}' after backoff...", failedTask.getName());
                // On retry success
                Consumer<Task> retrySuccess = t -> {
                    latch.countDown();
                    List<String> newlyReady = dependencyTracker.onTaskCompleted(t.getId());
                    for (String readyId : newlyReady) {
                        Task readyTask = currentJob.getTask(readyId);
                        if (readyTask != null) submitTask(readyTask);
                    }
                    if (currentJob.allTasksDone()) currentJob.markCompleted();
                };
                // On retry failure
                Consumer<Task> retryFailure = t -> {
                    if (!t.canRetry()) {
                        latch.countDown();
                        log.error("Task '{}' permanently DEAD after {} attempts.",
                                t.getName(), t.getAttemptCount());
                    } else {
                        handleFailure(t); // recurse for next retry
                    }
                };
                failedTask.resetToReady();
                threadPool.submit(new TaskWorker(
                        failedTask, retrySuccess, retryFailure, currentFailureRate));
            });
        } else {
            // DEAD — count down so latch doesn't hang
            latch.countDown();
            log.error("Task '{}' permanently DEAD after {} attempts.",
                    failedTask.getName(), failedTask.getAttemptCount());
        }
    }

    /**
     * Convenience method — no simulated failures.
     */
    public void execute(Job job) throws InterruptedException {
        execute(job, 0.0);
    }
}
