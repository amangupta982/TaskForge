package com.taskforge.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A single unit of work inside a Job.
 *
 * DSA Role: Node in the DAG.
 *
 * Fields:
 *   id            — unique within a job
 *   name          — human-readable label
 *   priority      — higher = picked first from ready queue (max-heap)
 *   durationMs    — estimated execution time in ms (used for critical path DP)
 *   maxRetries    — how many times to retry on failure
 *   attemptCount  — current retry count (AtomicInteger for thread safety)
 *   status        — current lifecycle state (AtomicReference for thread safety)
 */
public class Task implements Comparable<Task> {

    private final String id;
    private final String name;
    private final int priority;           // higher = more urgent
    private final long durationMs;        // estimated duration for critical path
    private final int maxRetries;

    // Thread-safe mutable fields
    private final AtomicInteger attemptCount = new AtomicInteger(0);
    private final AtomicReference<TaskStatus> status =
            new AtomicReference<>(TaskStatus.PENDING);

    // Timing
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String errorMessage;

    public Task(String id, String name, int priority, long durationMs, int maxRetries) {
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.durationMs = durationMs;
        this.maxRetries = maxRetries;
    }

    // Convenience constructor with defaults
    public Task(String id, String name) {
        this(id, name, 5, 1000L, 3);
    }

    /**
     * Comparable: higher priority = comes first in PriorityQueue (max-heap).
     * Java's PriorityQueue is a min-heap by default, so we invert the comparison.
     */
    @Override
    public int compareTo(Task other) {
        return Integer.compare(other.priority, this.priority); // reversed for max-heap
    }

    // ── Status transitions ────────────────────────────────────

    public boolean transitionTo(TaskStatus newStatus) {
        return status.compareAndSet(status.get(), newStatus);
    }

    public void markRunning() {
        status.set(TaskStatus.RUNNING);
        startedAt = Instant.now();
    }

    public void markDone() {
        status.set(TaskStatus.DONE);
        completedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.errorMessage = error;
        int attempts = attemptCount.incrementAndGet();
        if (attempts >= maxRetries) {
            status.set(TaskStatus.DEAD);
        } else {
            status.set(TaskStatus.FAILED);
        }
        completedAt = Instant.now();
    }

    public void resetToReady() {
        status.set(TaskStatus.READY);
        startedAt = null;
        completedAt = null;
    }

    public boolean canRetry() {
        return status.get() == TaskStatus.FAILED && attemptCount.get() < maxRetries;
    }

    /**
     * Backoff delay in ms before next retry attempt.
     * Formula: 2^attempt * 1000 ms (1s, 2s, 4s, 8s ...)
     */
    public long backoffDelayMs() {
        return (long) Math.pow(2, attemptCount.get()) * 1000L;
    }

    // ── Getters ──────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public int getPriority() { return priority; }
    public long getDurationMs() { return durationMs; }
    public int getMaxRetries() { return maxRetries; }
    public int getAttemptCount() { return attemptCount.get(); }
    public TaskStatus getStatus() { return status.get(); }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        return String.format("Task{id='%s', name='%s', priority=%d, status=%s}",
                id, name, priority, status.get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task)) return false;
        return this.id.equals(((Task) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
