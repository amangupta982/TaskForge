package com.taskforge.dto;

import com.taskforge.model.DAG;
import com.taskforge.model.Job;
import com.taskforge.model.Task;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Response DTO for GET /api/jobs/{id}/dag
 * Returns adjacency list + node metadata for frontend graph rendering.
 */
public class DAGResponse {

    private String jobId;
    private String jobName;
    private int totalNodes;
    private int totalEdges;
    private List<NodeInfo> nodes;
    private Map<String, List<String>> adjacencyList;
    private List<String> criticalPath;

    public static DAGResponse from(Job job) {
        DAGResponse r = new DAGResponse();
        r.jobId = job.getId();
        r.jobName = job.getName();
        r.totalNodes = job.getDag().getTaskCount();
        r.totalEdges = job.getDag().getEdgeCount();
        r.adjacencyList = job.getDag().copyAdjList();
        r.criticalPath = job.getCriticalPath();

        r.nodes = job.getTasks().values().stream()
                .map(task -> {
                    NodeInfo n = new NodeInfo();
                    n.id = task.getId();
                    n.name = task.getName();
                    n.priority = task.getPriority();
                    n.durationMs = task.getDurationMs();
                    n.status = task.getStatus().name();
                    n.inDegree = job.getDag().getInDegree(task.getId());
                    n.onCriticalPath = job.getCriticalPath().contains(task.getId());
                    return n;
                })
                .sorted((a, b) -> a.id.compareTo(b.id))
                .collect(Collectors.toList());

        return r;
    }

    public static class NodeInfo {
        public String id;
        public String name;
        public int priority;
        public long durationMs;
        public String status;
        public int inDegree;
        public boolean onCriticalPath;
    }

    // Getters
    public String getJobId() { return jobId; }
    public String getJobName() { return jobName; }
    public int getTotalNodes() { return totalNodes; }
    public int getTotalEdges() { return totalEdges; }
    public List<NodeInfo> getNodes() { return nodes; }
    public Map<String, List<String>> getAdjacencyList() { return adjacencyList; }
    public List<String> getCriticalPath() { return criticalPath; }
}
