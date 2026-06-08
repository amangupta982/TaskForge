package com.taskforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TaskForge — Distributed DAG Task Scheduler & Executor
 *
 * Implements:
 *   - DAG-based job modelling (adjacency list)
 *   - Kahn's BFS topological sort for execution ordering
 *   - DFS 3-color cycle detection
 *   - Critical path via DP on DAG (longest weighted path)
 *   - Priority scheduling via max-heap PriorityQueue
 *   - Parallel execution via Java ThreadPoolExecutor
 *   - Exponential backoff retry for failed tasks
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TaskForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskForgeApplication.class, args);
    }
}
