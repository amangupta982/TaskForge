package com.taskforge.service;

import com.taskforge.algorithm.CriticalPathFinder;
import com.taskforge.executor.TaskExecutor;
import com.taskforge.model.Job;
import com.taskforge.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(JobOrchestrator.class);

    @Autowired private DAGValidator validator;
    @Autowired private CriticalPathFinder criticalPathFinder;
    @Autowired private TaskExecutor taskExecutor;   // ← this is YOUR TaskExecutor class, now unambiguous

    public DAGValidator.ValidationResult validateJob(Job job) {
        log.info("Validating Job '{}' ({} tasks, {} edges)",
                job.getName(), job.getDag().getTaskCount(), job.getDag().getEdgeCount());

        DAGValidator.ValidationResult result = validator.validate(job);

        if (result.valid) {
            job.markValidated();
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

    @Async("jobAsyncExecutor")   // ← tells Spring to use our renamed async pool
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

    @Async("jobAsyncExecutor")
    public void executeAsync(Job job) {
        executeAsync(job, 0.0);
    }
}
