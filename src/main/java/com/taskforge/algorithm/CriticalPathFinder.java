package com.taskforge.algorithm;

import com.taskforge.model.DAG;
import com.taskforge.model.Job;
import com.taskforge.model.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Critical Path Analysis — Longest Weighted Path in a DAG
 *
 * DSA Concept:
 *   The critical path is the longest sequence of dependent tasks.
 *   It defines the MINIMUM possible time to complete the entire job,
 *   regardless of how many parallel threads are available.
 *
 *   Any task on the critical path is a bottleneck — delaying it delays
 *   the entire job. Tasks NOT on the critical path have slack (float).
 *
 * Algorithm: Dynamic Programming on topologically sorted nodes.
 *   dp[v] = max(dp[u] + duration[v]) for all predecessors u of v
 *
 * Steps:
 *   1. Topologically sort the DAG
 *   2. Process nodes in topo order
 *   3. For each node, dp[node] = max(dp[predecessor] + duration[node])
 *      across all predecessors
 *   4. The critical path ends at the node with max dp value
 *   5. Backtrack via parent[] to reconstruct the full path
 *
 * Complexity:
 *   Time:  O(V + E)  — one pass over topo order
 *   Space: O(V)      — dp table + parent tracking
 *
 * Real-world use: Google's Borg, Kubernetes job schedulers, Apache Airflow
 * all compute critical paths to estimate job completion time.
 */
@Component
public class CriticalPathFinder {

    @Autowired
    private TopologicalSorter topoSorter;

    public static class CriticalPathResult {
        public final List<String> path;           // task IDs on critical path
        public final long totalDurationMs;         // minimum job completion time
        public final Map<String, Long> taskEarliestStart; // earliest start per task

        public CriticalPathResult(List<String> path, long totalDurationMs,
                                   Map<String, Long> taskEarliestStart) {
            this.path = path;
            this.totalDurationMs = totalDurationMs;
            this.taskEarliestStart = taskEarliestStart;
        }
    }

    /**
     * Computes the critical path of the given Job's DAG.
     *
     * @param job the job containing tasks and DAG
     * @return CriticalPathResult with path, duration, and earliest start times
     */
    public CriticalPathResult findCriticalPath(Job job) {
        DAG dag = job.getDag();
        Map<String, Task> tasks = job.getTasks();

        // Step 1: Get topological order
        TopologicalSorter.TopoSortResult topoResult = topoSorter.sort(dag);
        if (!topoResult.success) {
            return new CriticalPathResult(Collections.emptyList(), 0, Collections.emptyMap());
        }

        List<String> topoOrder = topoResult.order;

        // Build reverse adjacency list (predecessor map) for DP
        Map<String, List<String>> predecessors = buildPredecessorMap(dag);

        // Step 2: DP — dp[node] = earliest completion time of this node
        Map<String, Long> dp = new HashMap<>();
        Map<String, String> parent = new HashMap<>(); // for path reconstruction
        Map<String, Long> earliestStart = new HashMap<>();

        for (String taskId : topoOrder) {
            Task task = tasks.get(taskId);
            long duration = (task != null) ? task.getDurationMs() : 1000L;

            long maxPredCompletion = 0L;
            String bestPred = null;

            for (String pred : predecessors.getOrDefault(taskId, Collections.emptyList())) {
                long predCompletion = dp.getOrDefault(pred, 0L);
                if (predCompletion > maxPredCompletion) {
                    maxPredCompletion = predCompletion;
                    bestPred = pred;
                }
            }

            earliestStart.put(taskId, maxPredCompletion);
            dp.put(taskId, maxPredCompletion + duration);
            if (bestPred != null) {
                parent.put(taskId, bestPred);
            }
        }

        // Step 3: Find the node with maximum dp value (end of critical path)
        String criticalEnd = topoOrder.stream()
                .max(Comparator.comparingLong(t -> dp.getOrDefault(t, 0L)))
                .orElse(null);

        if (criticalEnd == null) {
            return new CriticalPathResult(Collections.emptyList(), 0, earliestStart);
        }

        long totalDuration = dp.get(criticalEnd);

        // Step 4: Backtrack through parent[] to reconstruct path
        List<String> criticalPath = new LinkedList<>();
        String curr = criticalEnd;
        while (curr != null) {
            criticalPath.add(0, curr); // prepend for correct order
            curr = parent.get(curr);
        }

        return new CriticalPathResult(criticalPath, totalDuration, earliestStart);
    }

    /**
     * Builds a reverse adjacency list: taskId → list of predecessors.
     * Needed for the DP recurrence dp[v] = max(dp[u] + duration[v]).
     */
    private Map<String, List<String>> buildPredecessorMap(DAG dag) {
        Map<String, List<String>> predecessors = new HashMap<>();
        dag.getTaskIds().forEach(id -> predecessors.put(id, new ArrayList<>()));

        dag.copyAdjList().forEach((from, successors) ->
            successors.forEach(to ->
                predecessors.get(to).add(from)
            )
        );
        return predecessors;
    }
}
