package com.taskforge;

import com.taskforge.algorithm.CycleDetector;
import com.taskforge.algorithm.TopologicalSorter;
import com.taskforge.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying end-to-end job validation and execution logic.
 * Tests the model layer independently (no Spring context needed).
 */
class JobExecutionIntegrationTest {

    private CycleDetector cycleDetector;
    private TopologicalSorter topoSorter;

    @BeforeEach
    void setUp() {
        cycleDetector = new CycleDetector();
        topoSorter = new TopologicalSorter();
    }

    private Job buildCICDJob() {
        Job job = new Job("demo", "CI/CD Pipeline", "");
        job.addTask(new Task("t1", "Checkout Code",     10, 500L,  2));
        job.addTask(new Task("t2", "Compile Sources",    8, 1500L, 2));
        job.addTask(new Task("t3", "Run Unit Tests",     9, 2000L, 3));
        job.addTask(new Task("t4", "Lint & Style Check", 6, 800L,  1));
        job.addTask(new Task("t5", "Package Artifact",   7, 1000L, 2));
        job.addTask(new Task("t6", "Deploy to Staging",  5, 1500L, 3));

        job.addDependency("t1", "t2");
        job.addDependency("t2", "t3");
        job.addDependency("t2", "t4");
        job.addDependency("t3", "t5");
        job.addDependency("t4", "t5");
        job.addDependency("t5", "t6");
        return job;
    }

    @Test
    @DisplayName("CI/CD job DAG has no cycles")
    void testCICDJobNoCycle() {
        Job job = buildCICDJob();
        assertFalse(cycleDetector.hasCycle(job.getDag()));
    }

    @Test
    @DisplayName("CI/CD job produces valid topological order")
    void testCICDTopoOrder() {
        Job job = buildCICDJob();
        TopologicalSorter.TopoSortResult result = topoSorter.sort(job.getDag());

        assertTrue(result.success);
        assertEquals(6, result.order.size());

        List<String> order = result.order;
        // t1 must come before t2 (checkout before build)
        assertTrue(order.indexOf("t1") < order.indexOf("t2"));
        // t2 before t3 AND t4 (build before test and lint)
        assertTrue(order.indexOf("t2") < order.indexOf("t3"));
        assertTrue(order.indexOf("t2") < order.indexOf("t4"));
        // t3 and t4 before t5 (test and lint before package)
        assertTrue(order.indexOf("t3") < order.indexOf("t5"));
        assertTrue(order.indexOf("t4") < order.indexOf("t5"));
        // t5 before t6 (package before deploy)
        assertTrue(order.indexOf("t5") < order.indexOf("t6"));
    }

    @Test
    @DisplayName("Only t1 is initially ready (in-degree=0)")
    void testInitialReadyTasks() {
        Job job = buildCICDJob();
        List<String> ready = topoSorter.getInitialReadyTasks(job.getDag());
        assertEquals(1, ready.size());
        assertEquals("t1", ready.get(0));
    }

    @Test
    @DisplayName("Task priority comparator: higher priority dequeued first")
    void testTaskPriorityOrdering() {
        Task high   = new Task("h", "High",   10, 100L, 1);
        Task medium = new Task("m", "Medium",  5, 100L, 1);
        Task low    = new Task("l", "Low",     1, 100L, 1);

        // Task.compareTo is inverted → use PriorityQueue to verify max-heap behavior
        java.util.PriorityQueue<Task> pq = new java.util.PriorityQueue<>();
        pq.add(low);
        pq.add(high);
        pq.add(medium);

        assertEquals("h", pq.poll().getId()); // highest priority first
        assertEquals("m", pq.poll().getId());
        assertEquals("l", pq.poll().getId());
    }

    @Test
    @DisplayName("Task retry: backoff delay doubles with each attempt")
    void testExponentialBackoff() {
        Task task = new Task("t", "Flaky Task", 5, 100L, 5);

        // Simulate 3 failures
        task.markFailed("error 1"); // attempt 1 → delay = 2^1 = 2000ms
        long delay1 = task.backoffDelayMs();

        task.markFailed("error 2"); // attempt 2 → delay = 2^2 = 4000ms
        long delay2 = task.backoffDelayMs();

        task.markFailed("error 3"); // attempt 3 → delay = 2^3 = 8000ms
        long delay3 = task.backoffDelayMs();

        assertEquals(2000L, delay1);
        assertEquals(4000L, delay2);
        assertEquals(8000L, delay3);
        // Verify it's doubling
        assertEquals(delay1 * 2, delay2);
        assertEquals(delay2 * 2, delay3);
    }

    @Test
    @DisplayName("Task becomes DEAD after maxRetries exhausted")
    void testMaxRetriesExhausted() {
        Task task = new Task("t", "Doomed Task", 5, 100L, 2); // maxRetries = 2

        task.markFailed("fail 1"); // attempt 1, status = FAILED
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertTrue(task.canRetry());

        task.markFailed("fail 2"); // attempt 2, status = DEAD
        assertEquals(TaskStatus.DEAD, task.getStatus());
        assertFalse(task.canRetry());
    }

    @Test
    @DisplayName("Job progress tracking: completedTaskCount and progressPercent")
    void testJobProgressTracking() {
        Job job = buildCICDJob();
        assertEquals(0, job.getCompletedTaskCount());
        assertEquals(0.0, job.getProgressPercent(), 0.01);

        // Simulate 3 of 6 tasks completing
        job.getTask("t1").markRunning(); job.getTask("t1").markDone();
        job.getTask("t2").markRunning(); job.getTask("t2").markDone();
        job.getTask("t3").markRunning(); job.getTask("t3").markDone();

        assertEquals(3, job.getCompletedTaskCount());
        assertEquals(50.0, job.getProgressPercent(), 0.01);
        assertFalse(job.allTasksDone());
    }

    @Test
    @DisplayName("Cyclic job is correctly identified")
    void testCyclicJobDetected() {
        Job job = new Job("bad", "Bad Job", "");
        job.addTask(new Task("A", "A", 5, 100L, 1));
        job.addTask(new Task("B", "B", 5, 100L, 1));
        job.addTask(new Task("C", "C", 5, 100L, 1));
        job.addDependency("A", "B");
        job.addDependency("B", "C");
        job.addDependency("C", "A"); // cycle!

        assertTrue(cycleDetector.hasCycle(job.getDag()));

        TopologicalSorter.TopoSortResult result = topoSorter.sort(job.getDag());
        assertFalse(result.success); // topo sort fails on cyclic graph
    }
}
