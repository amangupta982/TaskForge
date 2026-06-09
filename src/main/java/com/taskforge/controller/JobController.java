package com.taskforge.controller;

import com.taskforge.dto.DAGResponse;
import com.taskforge.dto.JobStatusResponse;
import com.taskforge.dto.JobSubmitRequest;
import com.taskforge.model.Job;
import com.taskforge.service.JobService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JobController — REST API for job submission, execution, and monitoring.
 *
 * Endpoints:
 *   POST   /api/jobs                      → Submit a new job (create + validate)
 *   POST   /api/jobs/{id}/execute         → Start execution of a validated job
 *   GET    /api/jobs/{id}/status          → Real-time status + task progress
 *   GET    /api/jobs/{id}/dag             → DAG structure for visualization
 *   GET    /api/jobs                      → List all jobs
 *   DELETE /api/jobs/{id}                 → Cancel a job
 *   POST   /api/jobs/demo                 → Create and run the CI/CD demo job
 */
@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    @Autowired
    private JobService jobService;

    /**
     * POST /api/jobs
     * Submits a new job, builds the DAG, runs cycle detection + topo sort.
     * Returns 201 Created with the job ID on success, 400 if DAG is invalid.
     */
    @PostMapping
    public ResponseEntity<?> submitJob(@Valid @RequestBody JobSubmitRequest request) {
        try {
            Job job = jobService.createJob(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "jobId",        job.getId(),
                    "jobName",      job.getName(),
                    "status",       job.getStatus().name(),
                    "totalTasks",   job.getTasks().size(),
                    "criticalPath", job.getCriticalPath(),
                    "criticalPathDurationMs", job.getCriticalPathDurationMs(),
                    "message",      "Job created and validated. POST /api/jobs/" + job.getId() + "/execute to run."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/jobs/{id}/execute?failureRate=0.0
     * Triggers async execution of a validated job.
     * Returns immediately — poll /status to watch progress.
     *
     * @param failureRate optional simulated failure rate (0.0–1.0) for testing retries
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeJob(
            @PathVariable String id,
            @RequestParam(defaultValue = "0.0") double failureRate) {
        try {
            jobService.executeJob(id, failureRate);
            return ResponseEntity.ok(Map.of(
                    "jobId",   id,
                    "message", "Execution started. Poll GET /api/jobs/" + id + "/status for progress.",
                    "failureRate", failureRate
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/jobs/{id}/status
     * Returns real-time execution status including per-task states,
     * progress %, critical path, and timing info.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        try {
            Job job = jobService.getJobOrThrow(id);
            return ResponseEntity.ok(JobStatusResponse.from(job));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/jobs/{id}/dag
     * Returns the DAG adjacency list + node metadata.
     * Feed this to a Vis.js or D3 frontend to render the graph visually.
     */
    @GetMapping("/{id}/dag")
    public ResponseEntity<?> getDag(@PathVariable String id) {
        try {
            Job job = jobService.getJobOrThrow(id);
            return ResponseEntity.ok(DAGResponse.from(job));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/jobs
     * Lists all submitted jobs with summary info.
     */
    @GetMapping
    public ResponseEntity<?> listJobs() {
        List<Map<String, Object>> summary = jobService.getAllJobs().stream()
                .map(job -> Map.<String, Object>of(
                        "jobId",          job.getId(),
                        "name",           job.getName(),
                        "status",         job.getStatus().name(),
                        "totalTasks",     job.getTasks().size(),
                        "completedTasks", job.getCompletedTaskCount(),
                        "progress",       String.format("%.1f%%", job.getProgressPercent()),
                        "submittedAt",    job.getSubmittedAt().toString()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("jobs", summary, "total", summary.size()));
    }

    /**
     * DELETE /api/jobs/{id}
     * Cancels a job and all pending tasks.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelJob(@PathVariable String id) {
        try {
            jobService.cancelJob(id);
            return ResponseEntity.ok(Map.of(
                    "jobId",   id,
                    "message", "Job cancelled successfully."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/jobs/demo
     * Creates and immediately executes the built-in CI/CD pipeline demo.
     * Great for testing the API without writing JSON.
     */
    @PostMapping("/demo")
    public ResponseEntity<?> runDemo(
            @RequestParam(defaultValue = "0.0") double failureRate) {
        try {
            Job demo = jobService.createDemoJob();
            jobService.executeJob(demo.getId(), failureRate);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "jobId",        demo.getId(),
                    "jobName",      demo.getName(),
                    "status",       demo.getStatus().name(),
                    "totalTasks",   demo.getTasks().size(),
                    "criticalPath", demo.getCriticalPath(),
                    "criticalPathDurationMs", demo.getCriticalPathDurationMs(),
                    "message",      "Demo job started! Poll GET /api/jobs/" + demo.getId() + "/status"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
