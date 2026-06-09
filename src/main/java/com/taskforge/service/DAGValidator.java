package com.taskforge.service;

import com.taskforge.algorithm.CycleDetector;
import com.taskforge.algorithm.TopologicalSorter;
import com.taskforge.model.DAG;
import com.taskforge.model.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DAGValidator — Validates a Job's DAG before execution.
 *
 * Checks performed:
 *   1. Job must have at least one task
 *   2. No cycle exists (DFS 3-color detection)
 *   3. Topological sort succeeds (Kahn's BFS)
 *   4. All dependency references point to valid task IDs
 *
 * This is a Facade pattern — hides the complexity of running
 * multiple validation algorithms behind a single clean API.
 */
@Service
public class DAGValidator {

    @Autowired private CycleDetector cycleDetector;
    @Autowired private TopologicalSorter topoSorter;

    public static class ValidationResult {
        public final boolean valid;
        public final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failed(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    /**
     * Validates the job's DAG against all rules.
     * Returns a ValidationResult with all errors found (not just the first).
     */
    public ValidationResult validate(Job job) {
        List<String> errors = new ArrayList<>();
        DAG dag = job.getDag();

        // Rule 1: Must have at least one task
        if (job.getTasks().isEmpty()) {
            errors.add("Job must have at least one task.");
            return ValidationResult.failed(errors);
        }

        // Rule 2: Cycle detection (DFS 3-color)
        CycleDetector.CycleDetectionResult cycleResult = cycleDetector.detect(dag);
        if (cycleResult.hasCycle) {
            errors.add(String.format(
                "Cycle detected in DAG. Cycle path: %s. " +
                "Tasks cannot have circular dependencies.",
                cycleResult.cyclePath));
        }

        // Rule 3: Topological sort must succeed (additional cycle cross-check)
        if (errors.isEmpty()) {
            TopologicalSorter.TopoSortResult topoResult = topoSorter.sort(dag);
            if (!topoResult.success) {
                errors.add("Topological sort failed: " + topoResult.errorMessage);
            }
        }

        // Rule 4: All referenced task IDs must exist in the job
        dag.copyAdjList().forEach((fromId, successors) -> {
            if (!job.getTasks().containsKey(fromId)) {
                errors.add("Dependency references unknown task ID: " + fromId);
            }
            for (String toId : successors) {
                if (!job.getTasks().containsKey(toId)) {
                    errors.add("Dependency references unknown task ID: " + toId);
                }
            }
        });

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }
}
