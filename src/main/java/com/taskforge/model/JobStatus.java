package com.taskforge.model;

/**
 * Lifecycle states of a Job (the top-level unit containing a DAG of tasks).
 */
public enum JobStatus {
    CREATED,     // job submitted, not yet validated or executed
    VALIDATED,   // DAG validated (no cycles), ready to execute
    RUNNING,     // execution in progress
    COMPLETED,   // all tasks finished successfully
    FAILED,      // one or more tasks permanently failed (DEAD state)
    CANCELLED    // manually cancelled by user
}
