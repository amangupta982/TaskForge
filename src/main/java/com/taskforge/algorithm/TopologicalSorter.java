package com.taskforge.algorithm;

import com.taskforge.model.DAG;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Kahn's Algorithm — BFS-based Topological Sort
 *
 * DSA Concept:
 *   Topological sort gives a linear ordering of vertices such that
 *   for every directed edge u → v, vertex u comes before v.
 *
 *   This is the valid execution order for a DAG of tasks.
 *
 * Kahn's Algorithm Steps:
 *   1. Compute in-degree for every node
 *   2. Enqueue all nodes with in-degree = 0 (no dependencies)
 *   3. While queue is not empty:
 *      a. Dequeue node u, add to result
 *      b. For each neighbor v of u: decrement in-degree[v]
 *      c. If in-degree[v] == 0: enqueue v (all its deps are done)
 *   4. If result.size() < V: graph has a cycle (should never happen after CycleDetector)
 *
 * Complexity:
 *   Time:  O(V + E)
 *   Space: O(V)
 *
 * Note: We use a PriorityQueue (min-heap by task priority) instead of a plain
 * Queue so that among nodes with the same level of readiness, higher-priority
 * tasks appear earlier in the sort.
 */
@Component
public class TopologicalSorter {

    public static class TopoSortResult {
        public final boolean success;
        public final List<String> order;    // valid execution order
        public final String errorMessage;

        private TopoSortResult(boolean success, List<String> order, String errorMessage) {
            this.success = success;
            this.order = order;
            this.errorMessage = errorMessage;
        }

        public static TopoSortResult success(List<String> order) {
            return new TopoSortResult(true, order, null);
        }

        public static TopoSortResult failure(String message) {
            return new TopoSortResult(false, Collections.emptyList(), message);
        }
    }

    /**
     * Performs Kahn's topological sort on the DAG.
     *
     * @param dag the directed acyclic graph
     * @return TopoSortResult with the valid execution order
     */
    public TopoSortResult sort(DAG dag) {
        Map<String, Integer> inDegree = dag.copyInDegree();
        Map<String, List<String>> adjList = dag.copyAdjList();

        // Step 1: Enqueue all nodes with in-degree 0
        // Using a simple queue here (priority is handled at scheduling time)
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> topoOrder = new ArrayList<>();

        // Step 2: BFS
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            topoOrder.add(curr);

            // Reduce in-degree of all successors
            for (String successor : adjList.getOrDefault(curr, Collections.emptyList())) {
                int newInDegree = inDegree.merge(successor, -1, Integer::sum);
                if (newInDegree == 0) {
                    queue.offer(successor); // successor is now ready
                }
            }
        }

        // If we didn't process all nodes, there's a cycle
        if (topoOrder.size() != dag.getTaskCount()) {
            return TopoSortResult.failure(
                String.format("Topological sort incomplete: processed %d of %d tasks. Cycle detected.",
                    topoOrder.size(), dag.getTaskCount()));
        }

        return TopoSortResult.success(topoOrder);
    }

    /**
     * Returns all tasks that are immediately ready to execute —
     * i.e., those with in-degree 0 in the original DAG.
     * These are the first batch submitted to the thread pool.
     */
    public List<String> getInitialReadyTasks(DAG dag) {
        List<String> ready = new ArrayList<>();
        dag.copyInDegree().forEach((taskId, degree) -> {
            if (degree == 0) ready.add(taskId);
        });
        return ready;
    }
}
