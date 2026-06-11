package com.taskforge;

import com.taskforge.algorithm.CycleDetector;
import com.taskforge.model.DAG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DFS 3-color cycle detection.
 */
class CycleDetectorTest {

    private CycleDetector detector;

    @BeforeEach
    void setUp() { detector = new CycleDetector(); }

    private DAG buildLinearDAG() {
        // A → B → C → D (no cycle)
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B");
        dag.addTask("C"); dag.addTask("D");
        dag.addDependency("A", "B");
        dag.addDependency("B", "C");
        dag.addDependency("C", "D");
        return dag;
    }

    @Test
    @DisplayName("Linear DAG has no cycle")
    void testLinearNoCycle() {
        assertFalse(detector.hasCycle(buildLinearDAG()));
    }

    @Test
    @DisplayName("Diamond DAG has no cycle")
    void testDiamondNoCycle() {
        // A → B, A → C, B → D, C → D
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B");
        dag.addTask("C"); dag.addTask("D");
        dag.addDependency("A", "B");
        dag.addDependency("A", "C");
        dag.addDependency("B", "D");
        dag.addDependency("C", "D");

        assertFalse(detector.hasCycle(dag));
    }

    @Test
    @DisplayName("Direct cycle A → B → A detected")
    void testDirectCycle() {
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B");
        dag.addDependency("A", "B");
        dag.addDependency("B", "A"); // cycle!

        CycleDetector.CycleDetectionResult result = detector.detect(dag);
        assertTrue(result.hasCycle);
        assertFalse(result.cyclePath.isEmpty());
    }

    @Test
    @DisplayName("3-node cycle A → B → C → A detected")
    void testThreeNodeCycle() {
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B"); dag.addTask("C");
        dag.addDependency("A", "B");
        dag.addDependency("B", "C");
        dag.addDependency("C", "A"); // cycle!

        assertTrue(detector.hasCycle(dag));
    }

    @Test
    @DisplayName("Single node has no cycle")
    void testSingleNode() {
        DAG dag = new DAG();
        dag.addTask("A");
        assertFalse(detector.hasCycle(dag));
    }

    @Test
    @DisplayName("Disconnected graph with cycle in one component")
    void testDisconnectedWithCycle() {
        DAG dag = new DAG();
        // Component 1: no cycle
        dag.addTask("A"); dag.addTask("B");
        dag.addDependency("A", "B");
        // Component 2: has cycle
        dag.addTask("X"); dag.addTask("Y");
        dag.addDependency("X", "Y");
        dag.addDependency("Y", "X"); // cycle!

        assertTrue(detector.hasCycle(dag));
    }

    @Test
    @DisplayName("Task cannot add self-dependency")
    void testSelfDependencyThrows() {
        DAG dag = new DAG();
        dag.addTask("A");
        assertThrows(IllegalArgumentException.class, () -> dag.addDependency("A", "A"));
    }
}
