package com.taskforge.algorithm;

import com.taskforge.model.DAG;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DFS-based Cycle Detector using 3-color marking (WHITE / GRAY / BLACK).
 *
 * DSA Concept:
 *   This is the canonical way to detect back-edges in a directed graph.
 *
 *   WHITE = not yet visited
 *   GRAY  = currently in the DFS call stack (being processed)
 *   BLACK = fully processed (all descendants explored)
 *
 *   A cycle exists if and only if we encounter a GRAY node during DFS —
 *   meaning we've found a back-edge to an ancestor in the current path.
 *
 * Complexity:
 *   Time:  O(V + E)
 *   Space: O(V) for color map + recursion stack
 *
 * Why this matters:
 *   A DAG MUST have no cycles. If a cycle exists (e.g., A → B → C → A),
 *   execution would deadlock — tasks would wait forever for each other.
 *   We reject the job at submission time before any execution begins.
 */
@Component
public class CycleDetector {

    private enum Color { WHITE, GRAY, BLACK }

    public static class CycleDetectionResult {
        public final boolean hasCycle;
        public final List<String> cyclePath; // the cycle's node sequence, empty if no cycle

        private CycleDetectionResult(boolean hasCycle, List<String> cyclePath) {
            this.hasCycle = hasCycle;
            this.cyclePath = cyclePath;
        }

        public static CycleDetectionResult noCycle() {
            return new CycleDetectionResult(false, Collections.emptyList());
        }

        public static CycleDetectionResult cycleFound(List<String> path) {
            return new CycleDetectionResult(true, path);
        }
    }

    /**
     * Runs DFS 3-color cycle detection on the given DAG.
     *
     * @param dag the DAG to validate
     * @return CycleDetectionResult containing whether a cycle exists and the path
     */
    public CycleDetectionResult detect(DAG dag) {
        Map<String, Color> color = new HashMap<>();
        Map<String, String> parent = new HashMap<>();

        // Initialize all nodes as WHITE (unvisited)
        for (String taskId : dag.getTaskIds()) {
            color.put(taskId, Color.WHITE);
        }

        Map<String, List<String>> adjList = dag.copyAdjList();

        // Run DFS from every unvisited node (handles disconnected components)
        for (String taskId : dag.getTaskIds()) {
            if (color.get(taskId) == Color.WHITE) {
                List<String> cyclePath = new ArrayList<>();
                if (dfs(taskId, color, parent, adjList, cyclePath)) {
                    return CycleDetectionResult.cycleFound(cyclePath);
                }
            }
        }

        return CycleDetectionResult.noCycle();
    }

    /**
     * Recursive DFS that returns true if a cycle is found.
     * Builds the cycle path in cyclePath list.
     */
    private boolean dfs(String node,
                        Map<String, Color> color,
                        Map<String, String> parent,
                        Map<String, List<String>> adjList,
                        List<String> cyclePath) {

        // Mark as GRAY — currently on the DFS path
        color.put(node, Color.GRAY);

        for (String neighbor : adjList.getOrDefault(node, Collections.emptyList())) {

            if (color.get(neighbor) == Color.GRAY) {
                // Back-edge found → CYCLE detected!
                // Reconstruct cycle path from parent map
                cyclePath.add(neighbor);
                cyclePath.add(node);
                String curr = node;
                while (parent.containsKey(curr) && !parent.get(curr).equals(neighbor)) {
                    curr = parent.get(curr);
                    cyclePath.add(curr);
                }
                cyclePath.add(neighbor);
                Collections.reverse(cyclePath);
                return true;
            }

            if (color.get(neighbor) == Color.WHITE) {
                parent.put(neighbor, node);
                if (dfs(neighbor, color, parent, adjList, cyclePath)) {
                    return true;
                }
            }
            // BLACK nodes are fully processed — skip safely
        }

        // Mark as BLACK — fully processed
        color.put(node, Color.BLACK);
        return false;
    }

    /**
     * Simple boolean shorthand — use when you don't need the cycle path.
     */
    public boolean hasCycle(DAG dag) {
        return detect(dag).hasCycle;
    }
}
