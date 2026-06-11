package com.taskforge;

import com.taskforge.algorithm.CriticalPathFinder;
import com.taskforge.algorithm.TopologicalSorter;
import com.taskforge.model.Job;
import com.taskforge.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DP-based critical path analysis.
 */
class CriticalPathFinderTest {

    private CriticalPathFinder finder;

    @BeforeEach
    void setUp() {
        finder = new CriticalPathFinder();
        // Inject TopologicalSorter manually (simulating Spring DI)
        try {
            var field = CriticalPathFinder.class.getDeclaredField("topoSorter");
            field.setAccessible(true);
            field.set(finder, new TopologicalSorter());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Graph:
     *   A(1000ms) → B(2000ms) → D(500ms)
     *   A(1000ms) → C(3000ms) → D(500ms)
     *
     * Path via B: 1000 + 2000 + 500 = 3500ms
     * Path via C: 1000 + 3000 + 500 = 4500ms  ← critical path
     */
    @Test
    @DisplayName("Critical path chooses longest branch")
    void testCriticalPathChoosesLongest() {
        Job job = new Job("j1", "Test Job", "");
        job.addTask(new Task("A", "A", 5, 1000L, 1));
        job.addTask(new Task("B", "B", 5, 2000L, 1));
        job.addTask(new Task("C", "C", 5, 3000L, 1));
        job.addTask(new Task("D", "D", 5, 500L,  1));

        job.addDependency("A", "B");
        job.addDependency("A", "C");
        job.addDependency("B", "D");
        job.addDependency("C", "D");

        CriticalPathFinder.CriticalPathResult result = finder.findCriticalPath(job);

        assertEquals(4500L, result.totalDurationMs);
        assertTrue(result.path.contains("A"));
        assertTrue(result.path.contains("C"));
        assertTrue(result.path.contains("D"));
        assertFalse(result.path.contains("B")); // B is NOT on critical path
    }

    @Test
    @DisplayName("Linear chain: critical path is the entire chain")
    void testLinearChain() {
        Job job = new Job("j2", "Linear", "");
        job.addTask(new Task("A", "A", 5, 1000L, 1));
        job.addTask(new Task("B", "B", 5, 2000L, 1));
        job.addTask(new Task("C", "C", 5, 1500L, 1));
        job.addDependency("A", "B");
        job.addDependency("B", "C");

        CriticalPathFinder.CriticalPathResult result = finder.findCriticalPath(job);

        assertEquals(4500L, result.totalDurationMs);
        assertEquals(List.of("A", "B", "C"), result.path);
    }

    @Test
    @DisplayName("Single task: critical path is itself")
    void testSingleTask() {
        Job job = new Job("j3", "Single", "");
        job.addTask(new Task("A", "OnlyTask", 5, 3000L, 1));

        CriticalPathFinder.CriticalPathResult result = finder.findCriticalPath(job);

        assertEquals(3000L, result.totalDurationMs);
        assertEquals(List.of("A"), result.path);
    }

    @Test
    @DisplayName("Earliest start times are computed correctly")
    void testEarliestStartTimes() {
        // A(1000) → B(2000), A(1000) → C(500)
        Job job = new Job("j4", "EarliestStart", "");
        job.addTask(new Task("A", "A", 5, 1000L, 1));
        job.addTask(new Task("B", "B", 5, 2000L, 1));
        job.addTask(new Task("C", "C", 5, 500L,  1));
        job.addDependency("A", "B");
        job.addDependency("A", "C");

        CriticalPathFinder.CriticalPathResult result = finder.findCriticalPath(job);

        // A starts at 0, B and C can only start after A (1000ms)
        assertEquals(0L,    result.taskEarliestStart.get("A"));
        assertEquals(1000L, result.taskEarliestStart.get("B"));
        assertEquals(1000L, result.taskEarliestStart.get("C"));
    }
}
