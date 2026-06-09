package com.taskforge.dto;

import com.taskforge.model.Job;
import com.taskforge.model.Task;
import com.taskforge.model.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Response DTO for GET /api/jobs/{id}/status
 * Serialized to JSON by Spring Boot automatically.
 */
public class JobStatusResponse {

    private String jobId;
    private String jobName;
    private String status;
    private double progressPercent;
    private int totalTasks;
    private int completedTasks;
    private int failedTasks;
    private int runningTasks;
    private List<String> criticalPath;
    private long criticalPathDurationMs;
    private Instant submittedAt;
    private Instant startedAt;
    private Instant completedAt;
    private String failureReason;
    private List<TaskSummary> tasks;

    public static JobStatusResponse from(Job job) {
        JobStatusResponse r = new JobStatusResponse();
        r.jobId = job.getId();
        r.jobName = job.getName();
        r.status = job.getStatus().name();
        r.progressPercent = Math.round(job.getProgressPercent() * 10.0) / 10.0;
        r.totalTasks = job.getTasks().size();
        r.completedTasks = job.getCompletedTaskCount();
        r.failedTasks = job.getFailedTaskCount();
        r.runningTasks = (int) job.getTasks().values().stream()
                .filter(t -> t.getStatus() == TaskStatus.RUNNING).count();
        r.criticalPath = job.getCriticalPath();
        r.criticalPathDurationMs = job.getCriticalPathDurationMs();
        r.submittedAt = job.getSubmittedAt();
        r.startedAt = job.getStartedAt();
        r.completedAt = job.getCompletedAt();
        r.failureReason = job.getFailureReason();
        r.tasks = job.getTasks().values().stream()
                .map(TaskSummary::from)
                .sorted((a, b) -> a.taskId.compareTo(b.taskId))
                .collect(Collectors.toList());
        return r;
    }

    public static class TaskSummary {
        public String taskId;
        public String taskName;
        public String status;
        public int priority;
        public long durationMs;
        public int attemptCount;
        public int maxRetries;
        public Instant startedAt;
        public Instant completedAt;
        public String errorMessage;

        public static TaskSummary from(Task task) {
            TaskSummary s = new TaskSummary();
            s.taskId = task.getId();
            s.taskName = task.getName();
            s.status = task.getStatus().name();
            s.priority = task.getPriority();
            s.durationMs = task.getDurationMs();
            s.attemptCount = task.getAttemptCount();
            s.maxRetries = task.getMaxRetries();
            s.startedAt = task.getStartedAt();
            s.completedAt = task.getCompletedAt();
            s.errorMessage = task.getErrorMessage();
            return s;
        }
    }

    // Getters
    public String getJobId() { return jobId; }
    public String getJobName() { return jobName; }
    public String getStatus() { return status; }
    public double getProgressPercent() { return progressPercent; }
    public int getTotalTasks() { return totalTasks; }
    public int getCompletedTasks() { return completedTasks; }
    public int getFailedTasks() { return failedTasks; }
    public int getRunningTasks() { return runningTasks; }
    public List<String> getCriticalPath() { return criticalPath; }
    public long getCriticalPathDurationMs() { return criticalPathDurationMs; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
    public List<TaskSummary> getTasks() { return tasks; }
}
