package com.taskforge.service;

import com.taskforge.algorithm.CriticalPathFinder;
import com.taskforge.executor.TaskExecutor;
import com.taskforge.model.Job;
import com.taskforge.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * JobOrchestrator — Master Coordinator for Job Execution
 *
 * Coordinates the full job lifecycle:
 *   1. Validate DAG (cycle detection + topo sort check)
 *   2. Compute critical path (DP on DAG)
 *   3. Mark job as VALIDATED
 *   4. Async execute via thread pool (returns immediately, runs in background)
 *   5. Update job status on completion/failure
 *
 * @Async annotation: execute() runs in a separate Spring-managed thread pool
 * so the REST endpoint returns immediately — execution happens in background.
 * The client polls /api/jobs/{id}/status to check progress.
 *
 * Design Pattern: Facade + Command
 *   Facades the complexity of validation, analysis, and execution.
 *   The execute() method is effectively a Command object.
 */
@Service
public class JobOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(JobOrchestrator.class);

    @Autowired private DAGValidator validator;
    @Autowired private CriticalPathFinder criticalPathFinder;
    @Autowired private TaskExecutor taskExecutor;

    /**
     * Validates the job and returns any errors.
     * If valid, marks job as VALIDATED and computes critical path.
     *
     * @return ValidationResult with errors, or empty if valid
     */
    public DAGValidator.ValidationResult validateJob(Job job) {
        log.info("Validating Job '{}' ({} tasks, {} edges)",
                job.getName(), job.getDag().getTaskCount(), job.getDag().getEdgeCount());

        DAGValidator.ValidationResult result = validator.validate(job);

        if (result.valid) {
            job.markValidated();

            // Compute critical path (DP on DAG)
            CriticalPathFinder.CriticalPathResult cpResult =
                    criticalPathFinder.findCriticalPath(job);
            job.setCriticalPath(cpResult.path, cpResult.totalDurationMs);

            log.info("Job '{}' validated. Critical path: {} ({}ms)",
                    job.getName(), cpResult.path, cpResult.totalDurationMs);
        } else {
            log.warn("Job '{}' validation FAILED: {}", job.getName(), result.errors);
        }

        return result;
    }

    /**
     * Submits a validated job for asynchronous execution.
     * Returns immediately — execution happens on a background thread.
     *
     * @Async — Spring executes this in a separate thread from its async pool.
     */
    @Async
    public void executeAsync(Job job, double failureRate) {
        if (job.getStatus() != JobStatus.VALIDATED) {
            log.error("Cannot execute job '{}' — status is {} (must be VALIDATED)",
                    job.getName(), job.getStatus());
            return;
        }

        log.info("Beginning async execution of Job '{}'...", job.getName());
        job.markRunning();

        try {
            taskExecutor.execute(job, failureRate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.markFailed("Execution interrupted: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error executing job '{}': {}", job.getName(), e.getMessage());
            job.markFailed("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Convenience method with no simulated failures.
     */
    @Async
    public void executeAsync(Job job) {
        executeAsync(job, 0.0);
    }
}
