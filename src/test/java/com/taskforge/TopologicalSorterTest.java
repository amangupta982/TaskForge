package com.taskforge;

import com.taskforge.algorithm.TopologicalSorter;
import com.taskforge.model.DAG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Kahn's topological sort algorithm.
 */
class TopologicalSorterTest {

    private TopologicalSorter sorter;

    @BeforeEach
    void setUp() { sorter = new TopologicalSorter(); }

    @Test
    @DisplayName("Linear chain: A→B→C produces [A,B,C]")
    void testLinearChain() {
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B"); dag.addTask("C");
        dag.addDependency("A", "B");
        dag.addDependency("B", "C");

        TopologicalSorter.TopoSortResult result = sorter.sort(dag);

        assertTrue(result.success);
        assertEquals(3, result.order.size());
        // A must come before B, B before C
        assertTrue(result.order.indexOf("A") < result.order.indexOf("B"));
        assertTrue(result.order.indexOf("B") < result.order.indexOf("C"));
    }

    @Test
    @DisplayName("Diamond DAG: A must be first, D must be last")
    void testDiamondDAG() {
        // A → B, A → C, B → D, C → D
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B");
        dag.addTask("C"); dag.addTask("D");
        dag.addDependency("A", "B");
        dag.addDependency("A", "C");
        dag.addDependency("B", "D");
        dag.addDependency("C", "D");

        TopologicalSorter.TopoSortResult result = sorter.sort(dag);

        assertTrue(result.success);
        assertEquals(4, result.order.size());
        assertEquals("A", result.order.get(0));  // A has no deps, must be first
        assertEquals("D", result.order.get(3));  // D depends on all, must be last
    }

    @Test
    @DisplayName("CI/CD pipeline: correct ordering")
    void testCICDPipeline() {
        // checkout→build→[test,lint]→package→deploy
        DAG dag = new DAG();
        for (String t : List.of("checkout","build","test","lint","package","deploy"))
            dag.addTask(t);

        dag.addDependency("checkout", "build");
        dag.addDependency("build",    "test");
        dag.addDependency("build",    "lint");
        dag.addDependency("test",     "package");
        dag.addDependency("lint",     "package");
        dag.addDependency("package",  "deploy");

        TopologicalSorter.TopoSortResult result = sorter.sort(dag);

        assertTrue(result.success);
        List<String> order = result.order;

        // Verify all dependency constraints
        assertTrue(order.indexOf("checkout") < order.indexOf("build"));
        assertTrue(order.indexOf("build")    < order.indexOf("test"));
        assertTrue(order.indexOf("build")    < order.indexOf("lint"));
        assertTrue(order.indexOf("test")     < order.indexOf("package"));
        assertTrue(order.indexOf("lint")     < order.indexOf("package"));
        assertTrue(order.indexOf("package")  < order.indexOf("deploy"));
    }

    @Test
    @DisplayName("Single node returns order with that node")
    void testSingleNode() {
        DAG dag = new DAG();
        dag.addTask("solo");

        TopologicalSorter.TopoSortResult result = sorter.sort(dag);
        assertTrue(result.success);
        assertEquals(List.of("solo"), result.order);
    }

    @Test
    @DisplayName("getInitialReadyTasks returns nodes with in-degree 0")
    void testInitialReadyTasks() {
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B"); dag.addTask("C");
        dag.addDependency("A", "B");
        dag.addDependency("A", "C");
        // Only A has in-degree 0

        List<String> ready = sorter.getInitialReadyTasks(dag);
        assertEquals(1, ready.size());
        assertEquals("A", ready.get(0));
    }

    @Test
    @DisplayName("Parallel root tasks all appear in initial ready set")
    void testMultipleInitialReadyTasks() {
        DAG dag = new DAG();
        dag.addTask("A"); dag.addTask("B"); dag.addTask("C");
        dag.addTask("D");
        // A, B, C all independent; D depends on all three
        dag.addDependency("A", "D");
        dag.addDependency("B", "D");
        dag.addDependency("C", "D");

        List<String> ready = sorter.getInitialReadyTasks(dag);
        assertEquals(3, ready.size());
        assertTrue(ready.containsAll(List.of("A", "B", "C")));
    }
}
