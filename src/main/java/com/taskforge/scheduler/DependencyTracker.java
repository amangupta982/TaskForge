package com.taskforge.scheduler;

import com.taskforge.model.DAG;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DependencyTracker — Thread-Safe In-Degree Counter
 *
 * DSA Concept:
 *   At runtime, this replicates Kahn's in-degree[] array but in a
 *   thread-safe form using ConcurrentHashMap + AtomicInteger.
 *
 *   When a task completes:
 *     1. Look up its successors in the DAG
 *     2. Atomically decrement each successor's remaining dependency count
 *     3. Any successor reaching 0 is now READY — add to the priority queue
 *
 *   This is the core mechanism that drives parallel execution correctly:
 *   multiple threads can complete tasks simultaneously and each one safely
 *   updates the dependency counts without race conditions.
 *
 * Thread safety:
 *   AtomicInteger.decrementAndGet() is a single atomic CAS operation —
 *   no synchronized block needed.
 */
@Component
public class DependencyTracker {

    // taskId → remaining number of incomplete predecessors
    private final ConcurrentHashMap<String, AtomicInteger> remainingDeps
            = new ConcurrentHashMap<>();

    private DAG dag;

    /**
     * Initializes the tracker from a DAG's in-degree map.
     * Call once per job execution before starting the thread pool.
     */
    public void initialize(DAG dag) {
        this.dag = dag;
        remainingDeps.clear();
        dag.copyInDegree().forEach((taskId, degree) ->
            remainingDeps.put(taskId, new AtomicInteger(degree))
        );
    }

    /**
     * Called when a task completes successfully.
     * Decrements the remaining dependency count for all successors.
     *
     * @param completedTaskId the task that just finished
     * @return list of task IDs that are now READY (their dep count hit 0)
     */
    public List<String> onTaskCompleted(String completedTaskId) {
        List<String> newlyReady = new ArrayList<>();

        for (String successor : dag.getSuccessors(completedTaskId)) {
            AtomicInteger counter = remainingDeps.get(successor);
            if (counter != null) {
                int remaining = counter.decrementAndGet();
                if (remaining == 0) {
                    newlyReady.add(successor);
                }
            }
        }

        return newlyReady;
    }

    /**
     * Returns current remaining dependency count for a task.
     */
    public int getRemainingDeps(String taskId) {
        AtomicInteger counter = remainingDeps.get(taskId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Returns all tasks currently at zero remaining dependencies.
     * Used to bootstrap the initial ready set.
     */
    public List<String> getInitialReadyTasks() {
        List<String> ready = new ArrayList<>();
        remainingDeps.forEach((taskId, counter) -> {
            if (counter.get() == 0) ready.add(taskId);
        });
        return ready;
    }

    public void reset() {
        remainingDeps.clear();
        dag = null;
    }
}
