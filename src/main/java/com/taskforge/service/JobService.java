package com.taskforge.service;

import com.taskforge.dto.JobSubmitRequest;
import com.taskforge.model.Job;
import com.taskforge.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JobService — CRUD operations and execution lifecycle for Jobs.
 *
 * Maintains an in-memory job registry (ConcurrentHashMap).
 * In production this would be backed by a database (PostgreSQL + JPA).
 *
 * Responsibilities:
 *   - Create and register new jobs from API requests
 *   - Validate and execute jobs via JobOrchestrator
 *   - Look up jobs by ID
 *   - Cancel running jobs
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    // In-memory store: jobId → Job
    private final Map<String, Job> jobRegistry = new ConcurrentHashMap<>();

    @Autowired private JobOrchestrator orchestrator;

    /**
     * Creates a new Job from a submit request, validates it, and returns it.
     * Does NOT start execution — call executeJob() separately.
     */
    public Job createJob(JobSubmitRequest request) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        Job job = new Job(jobId, request.getName(), request.getDescription());

        // Add all tasks
        for (JobSubmitRequest.TaskDefinition taskDef : request.getTasks()) {
            Task task = new Task(
                    taskDef.getId(),
                    taskDef.getName(),
                    taskDef.getPriority(),
                    taskDef.getDurationMs(),
                    taskDef.getMaxRetries()
            );
            job.addTask(task);
        }

        // Add all dependencies
        for (JobSubmitRequest.DependencyDefinition dep : request.getDependencies()) {
            job.addDependency(dep.getFrom(), dep.getTo());
        }

        // Validate DAG
        DAGValidator.ValidationResult validation = orchestrator.validateJob(job);
        if (!validation.valid) {
            throw new IllegalArgumentException(
                "Invalid job DAG: " + String.join("; ", validation.errors));
        }

        jobRegistry.put(jobId, job);
        log.info("Job '{}' created and validated. ID={}", job.getName(), jobId);
        return job;
    }

    /**
     * Triggers async execution of an already-created and validated job.
     */
    public void executeJob(String jobId, double failureRate) {
        Job job = getJobOrThrow(jobId);
        log.info("Executing job '{}' (failureRate={})", job.getName(), failureRate);
        orchestrator.executeAsync(job, failureRate);
    }

    public void executeJob(String jobId) {
        executeJob(jobId, 0.0);
    }

    public Job getJobOrThrow(String jobId) {
        Job job = jobRegistry.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job;
    }

    public Collection<Job> getAllJobs() {
        return jobRegistry.values();
    }

    /**
     * Cancels a job if it's not yet completed.
     */
    public void cancelJob(String jobId) {
        Job job = getJobOrThrow(jobId);
        job.markCancelled();
        log.info("Job '{}' cancelled.", job.getName());
    }

    /**
     * Creates a sample CI/CD-style demo job for testing the API.
     * Pipeline: checkout → build → [test, lint] → package → deploy
     */
    public Job createDemoJob() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setName("CI/CD Pipeline Demo");
        req.setDescription("Checkout → Build → Test+Lint → Package → Deploy");

        req.setTasks(java.util.List.of(
            new JobSubmitRequest.TaskDefinition("t1", "Checkout Code",    10, 500L,  2),
            new JobSubmitRequest.TaskDefinition("t2", "Compile Sources",   8, 1500L, 2),
            new JobSubmitRequest.TaskDefinition("t3", "Run Unit Tests",    9, 2000L, 3),
            new JobSubmitRequest.TaskDefinition("t4", "Lint & Style Check",6, 800L,  1),
            new JobSubmitRequest.TaskDefinition("t5", "Package Artifact",  7, 1000L, 2),
            new JobSubmitRequest.TaskDefinition("t6", "Deploy to Staging", 5, 1500L, 3)
        ));

        req.setDependencies(java.util.List.of(
            new JobSubmitRequest.DependencyDefinition("t1", "t2"),  // checkout → build
            new JobSubmitRequest.DependencyDefinition("t2", "t3"),  // build → test
            new JobSubmitRequest.DependencyDefinition("t2", "t4"),  // build → lint (parallel)
            new JobSubmitRequest.DependencyDefinition("t3", "t5"),  // test → package
            new JobSubmitRequest.DependencyDefinition("t4", "t5"),  // lint → package
            new JobSubmitRequest.DependencyDefinition("t5", "t6")   // package → deploy
        ));

        return createJob(req);
    }
}
