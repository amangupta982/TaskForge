package com.taskforge.model;

import java.util.*;

/**
 * Directed Acyclic Graph (DAG) for modelling task dependencies.
 *
 * DSA: Adjacency List representation.
 *   - adjList   : taskId → list of taskIds it must run BEFORE (outgoing edges)
 *   - inDegree  : taskId → number of tasks that must finish BEFORE it (incoming edges)
 *
 * Example:
 *   Task A must finish before B and C:
 *     adjList[A] = [B, C]
 *     inDegree[B]++, inDegree[C]++
 *
 * Space: O(V + E)
 * Neighbor lookup: O(1)
 */
public class DAG {

    // taskId → set of taskIds that depend on it (successors)
    private final Map<String, List<String>> adjList;

    // taskId → count of unfinished predecessors
    private final Map<String, Integer> inDegree;

    // all registered task IDs
    private final Set<String> taskIds;

    public DAG() {
        this.adjList = new HashMap<>();
        this.inDegree = new HashMap<>();
        this.taskIds = new LinkedHashSet<>();
    }

    /**
     * Registers a task node in the DAG.
     * Must be called before adding edges.
     */
    public void addTask(String taskId) {
        taskIds.add(taskId);
        adjList.putIfAbsent(taskId, new ArrayList<>());
        inDegree.putIfAbsent(taskId, 0);
    }

    /**
     * Adds a directed dependency edge: fromId → toId
     * Meaning: task 'fromId' must complete BEFORE task 'toId' can start.
     *
     * @param fromId predecessor task
     * @param toId   successor task (depends on fromId)
     */
    public void addDependency(String fromId, String toId) {
        if (!taskIds.contains(fromId) || !taskIds.contains(toId)) {
            throw new IllegalArgumentException(
                String.format("Task not found: %s or %s", fromId, toId));
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("A task cannot depend on itself: " + fromId);
        }
        adjList.get(fromId).add(toId);
        inDegree.merge(toId, 1, Integer::sum);
    }

    /**
     * Returns a COPY of the in-degree map.
     * Callers (Kahn's, DependencyTracker) mutate their own copy during execution.
     */
    public Map<String, Integer> copyInDegree() {
        return new HashMap<>(inDegree);
    }

    /**
     * Returns a COPY of the adjacency list.
     * Safe to read from multiple threads without synchronization.
     */
    public Map<String, List<String>> copyAdjList() {
        Map<String, List<String>> copy = new HashMap<>();
        adjList.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    public List<String> getSuccessors(String taskId) {
        return adjList.getOrDefault(taskId, Collections.emptyList());
    }

    public int getInDegree(String taskId) {
        return inDegree.getOrDefault(taskId, 0);
    }

    public Set<String> getTaskIds() {
        return Collections.unmodifiableSet(taskIds);
    }

    public int getTaskCount() { return taskIds.size(); }

    public int getEdgeCount() {
        return adjList.values().stream().mapToInt(List::size).sum();
    }

    @Override
    public String toString() {
        return String.format("DAG{tasks=%d, edges=%d}", getTaskCount(), getEdgeCount());
    }
}
