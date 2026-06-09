package com.taskforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Request body for POST /api/jobs
 *
 * Example JSON:
 * {
 *   "name": "CI/CD Pipeline",
 *   "description": "Build and deploy pipeline",
 *   "tasks": [
 *     { "id": "t1", "name": "Checkout",  "priority": 10, "durationMs": 500,  "maxRetries": 2 },
 *     { "id": "t2", "name": "Build",     "priority": 8,  "durationMs": 1500, "maxRetries": 2 },
 *     { "id": "t3", "name": "Test",      "priority": 9,  "durationMs": 2000, "maxRetries": 3 }
 *   ],
 *   "dependencies": [
 *     { "from": "t1", "to": "t2" },
 *     { "from": "t2", "to": "t3" }
 *   ]
 * }
 */
public class JobSubmitRequest {

    @NotBlank(message = "Job name is required")
    private String name;

    private String description = "";

    @NotNull
    private List<TaskDefinition> tasks = new ArrayList<>();

    private List<DependencyDefinition> dependencies = new ArrayList<>();

    // ── Inner classes ─────────────────────────────────────

    public static class TaskDefinition {
        private String id;
        private String name;
        private int priority = 5;
        private long durationMs = 1000L;
        private int maxRetries = 3;

        public TaskDefinition() {}

        public TaskDefinition(String id, String name, int priority,
                               long durationMs, int maxRetries) {
            this.id = id;
            this.name = name;
            this.priority = priority;
            this.durationMs = durationMs;
            this.maxRetries = maxRetries;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public static class DependencyDefinition {
        private String from;
        private String to;

        public DependencyDefinition() {}

        public DependencyDefinition(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
    }

    // ── Getters / Setters ─────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<TaskDefinition> getTasks() { return tasks; }
    public void setTasks(List<TaskDefinition> tasks) { this.tasks = tasks; }
    public List<DependencyDefinition> getDependencies() { return dependencies; }
    public void setDependencies(List<DependencyDefinition> dependencies) { this.dependencies = dependencies; }
}
