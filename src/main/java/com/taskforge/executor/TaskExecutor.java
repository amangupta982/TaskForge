package com.taskforge.executor;

import com.taskforge.model.Task;
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
 *   1. Dequeue highest-priority ready task from PriorityTaskScheduler
 *   2. Wrap it in TaskWorker (a Runnable)
 *   3. Submit to ThreadPoolExecutor → runs on a background thread
 *   4. On task success: DependencyTracker finds newly-ready successors
 *                       → enqueue them to PriorityTaskScheduler
 *   5. On task failure: RetryManager schedules retry with backoff
 *   6. Repeat until all tasks are DONE or DEAD
 *
 * Key Java concurrency tools used:
 *   - ThreadPoolExecutor:   manages worker threads
 *   - CountDownLatch:       blocks main thread until all tasks complete
 *   - AtomicInteger:        thread-safe task completion counter
 *   - ConcurrentHashMap:    thread-safe task status registry (in Job)
 */
@Component
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);
    private static final int THREAD_POOL_SIZE =
            Runtime.getRuntime().availableProcessors();

    @Autowired private PriorityTaskScheduler scheduler;
    @Autowired private DependencyTracker dependencyTracker;
    @Autowired private RetryManager retryManager;

    /**
     * Executes an entire Job asynchronously.
     * Blocks the calling thread until all tasks are either DONE or DEAD.
     *
     * @param job                  the job to execute
     * @param simulatedFailureRate fraction of tasks that will fail (for testing)
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public void execute(Job job, double simulatedFailureRate) throws InterruptedException {
        int totalTasks = job.getTasks().size();
        log.info("Starting execution of Job '{}' with {} tasks on {} threads",
                job.getName(), totalTasks, THREAD_POOL_SIZE);

        // CountDownLatch: main thread waits until all tasks reach a terminal state
        CountDownLatch latch = new CountDownLatch(totalTasks);

        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("taskforge-worker-" + t.getId());
                    t.setDaemon(true);
                    return t;
                });

        // Initialize dependency tracker with this job's DAG
        dependencyTracker.initialize(job.getDag());

        // Define success handler — called when a task finishes successfully
        Consumer<Task> onSuccess = completedTask -> {
            latch.countDown();
            log.debug("Task '{}' done. Latch count: {}", completedTask.getName(), latch.getCount());

            // Find successors that are now unblocked
            List<String> newlyReady = dependencyTracker.onTaskCompleted(completedTask.getId());

            // Enqueue newly-ready tasks into the priority queue
            for (String readyId : newlyReady) {
                Task readyTask = job.getTask(readyId);
                if (readyTask != null) {
                    log.info("Task '{}' is now READY (dependency '{}' completed)",
                            readyTask.getName(), completedTask.getName());
                    scheduler.enqueue(readyTask);
                    // Submit to thread pool immediately
                    threadPool.submit(new TaskWorker(
                            readyTask, onSuccess, buildFailureHandler(job, latch, threadPool, simulatedFailureRate), simulatedFailureRate));
                }
            }

            // Check if job is fully complete
            if (job.allTasksDone()) {
                job.markCompleted();
                log.info("Job '{}' COMPLETED successfully!", job.getName());
            }
        };

        // Define failure handler
        Consumer<Task> onFailure = buildFailureHandler(job, latch, threadPool, simulatedFailureRate);

        // Submit all initially-ready tasks (in-degree = 0)
        List<String> initialReady = dependencyTracker.getInitialReadyTasks();
        log.info("Initial ready tasks (in-degree=0): {}", initialReady);

        for (String taskId : initialReady) {
            Task task = job.getTask(taskId);
            if (task != null) {
                scheduler.enqueue(task);
                threadPool.submit(new TaskWorker(task, onSuccess, onFailure, simulatedFailureRate));
            }
        }

        // Block until all tasks finish (or timeout after 5 minutes)
        boolean finished = latch.await(5, TimeUnit.MINUTES);
        threadPool.shutdown();

        if (!finished) {
            log.error("Job '{}' timed out after 5 minutes.", job.getName());
            job.markFailed("Execution timed out after 5 minutes.");
        } else if (job.hasDeadTask()) {
            job.markFailed("One or more tasks permanently failed.");
        }
    }

    private Consumer<Task> buildFailureHandler(Job job, CountDownLatch latch,
                                                ExecutorService pool, double failureRate) {
        return failedTask -> {
            if (failedTask.canRetry()) {
                // Schedule retry with backoff — re-enqueues task after delay
                retryManager.scheduleRetry(failedTask, () -> {
                    pool.submit(new TaskWorker(
                            failedTask,
                            t -> {
                                latch.countDown();
                                if (job.allTasksDone()) job.markCompleted();
                            },
                            t -> {
                                if (!t.canRetry()) latch.countDown();
                            },
                            failureRate
                    ));
                });
            } else {
                // DEAD — count it down so latch doesn't hang forever
                latch.countDown();
                log.error("Task '{}' permanently DEAD after {} attempts.",
                        failedTask.getName(), failedTask.getAttemptCount());
            }
        };
    }

    /**
     * Convenience method with no simulated failures.
     */
    public void execute(Job job) throws InterruptedException {
        execute(job, 0.0);
    }
}
