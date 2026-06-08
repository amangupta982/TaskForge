package com.taskforge.model;

/**
 * Lifecycle states of a single task inside a Job's DAG.
 *
 * State transitions:
 *   PENDING  → READY     (all dependencies completed)
 *   READY    → RUNNING   (picked up by thread pool)
 *   RUNNING  → DONE      (task logic completed successfully)
 *   RUNNING  → FAILED    (task logic threw an exception)
 *   FAILED   → READY     (retry scheduled via exponential backoff)
 *   FAILED   → DEAD      (max retries exhausted — terminal state)
 */
public enum TaskStatus {
    PENDING,    // waiting for dependencies to finish
    READY,      // dependencies done, sitting in priority queue
    RUNNING,    // currently executing on a thread
    DONE,       // completed successfully
    FAILED,     // threw exception — may retry
    DEAD,       // max retries exhausted — permanently failed
    CANCELLED   // job was cancelled before this task ran
}
