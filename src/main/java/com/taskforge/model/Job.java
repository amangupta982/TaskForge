package com.taskforge.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Job is the top-level unit of work — a named collection of Tasks
 * wired together into a DAG.
 *
 * Contains:
 *   - tasks     : taskId → Task (ConcurrentHashMap for thread-safe reads)
 *   - dag       : the dependency graph
 *   - status    : overall job lifecycle state
 *   - metadata  : name, description, submittedAt, startedAt, completedAt
 */
public class Job {

    private final String id;
    private final String name;
    private final String description;
    private final Instant submittedAt;

    // Thread-safe task registry
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    // The dependency graph
    private final DAG dag = new DAG();

    private final AtomicReference<JobStatus> status =
            new AtomicReference<>(JobStatus.CREATED);

    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String failureReason;

    // Critical path result (set after analysis)
    private volatile List<String> criticalPath = new ArrayList<>();
    private volatile long criticalPathDurationMs = 0;

    public Job(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.submittedAt = Instant.now();
    }

    // ── Builder-style setup methods ──────────────────────────

    public Job addTask(Task task) {
        tasks.put(task.getId(), task);
        dag.addTask(task.getId());
        return this;
    }

    /**
     * Adds dependency: predecessor must finish before successor.
     * @param predecessorId task that must complete first
     * @param successorId   task that waits for predecessor
     */
    public Job addDependency(String predecessorId, String successorId) {
        dag.addDependency(predecessorId, successorId);
        return this;
    }

    // ── Status management ────────────────────────────────────

    public void markValidated() {
        status.set(JobStatus.VALIDATED);
    }

    public void markRunning() {
        status.set(JobStatus.RUNNING);
        startedAt = Instant.now();
    }

    public void markCompleted() {
        status.set(JobStatus.COMPLETED);
        completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        status.set(JobStatus.FAILED);
        completedAt = Instant.now();
        this.failureReason = reason;
    }

    public void markCancelled() {
        status.set(JobStatus.CANCELLED);
        completedAt = Instant.now();
        tasks.values().stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING
                      || t.getStatus() == TaskStatus.READY)
            .forEach(t -> t.transitionTo(TaskStatus.CANCELLED));
    }

    // ── Query helpers ────────────────────────────────────────

    public int getCompletedTaskCount() {
        return (int) tasks.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .count();
    }

    public int getFailedTaskCount() {
        return (int) tasks.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.DEAD)
                .count();
    }

    public double getProgressPercent() {
        if (tasks.isEmpty()) return 0.0;
        return (getCompletedTaskCount() * 100.0) / tasks.size();
    }

    public boolean allTasksDone() {
        return tasks.values().stream()
                .allMatch(t -> t.getStatus() == TaskStatus.DONE);
    }

    public boolean hasDeadTask() {
        return tasks.values().stream()
                .anyMatch(t -> t.getStatus() == TaskStatus.DEAD);
    }

    // ── Getters ──────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public JobStatus getStatus() { return status.get(); }
    public String getFailureReason() { return failureReason; }
    public Map<String, Task> getTasks() { return Collections.unmodifiableMap(tasks); }
    public Task getTask(String taskId) { return tasks.get(taskId); }
    public DAG getDag() { return dag; }
    public List<String> getCriticalPath() { return criticalPath; }
    public long getCriticalPathDurationMs() { return criticalPathDurationMs; }

    public void setCriticalPath(List<String> path, long durationMs) {
        this.criticalPath = path;
        this.criticalPathDurationMs = durationMs;
    }

    @Override
    public String toString() {
        return String.format("Job{id='%s', name='%s', tasks=%d, status=%s}",
                id, name, tasks.size(), status.get());
    }
}
