package com.taskforge.scheduler;

import com.taskforge.model.Task;
import com.taskforge.model.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PriorityTaskScheduler — Max-Heap Ready Queue
 *
 * DSA Concept:
 *   A max-heap PriorityQueue where Tasks are ordered by their priority field.
 *   Higher priority tasks are dequeued first and submitted to the thread pool first.
 *
 *   Java's PriorityQueue is a MIN-heap by default.
 *   Task.compareTo() inverts the comparison to create a MAX-heap behavior:
 *     compareTo returns: other.priority - this.priority
 *     (so higher priority = smaller compareTo value = dequeued first)
 *
 * Thread Safety:
 *   PriorityQueue is NOT thread-safe. We protect it with a ReentrantLock
 *   so multiple worker threads can safely enqueue newly-ready tasks.
 *
 * Operations:
 *   enqueue(task)  — O(log N): adds task to the heap
 *   dequeue()      — O(log N): removes and returns highest-priority ready task
 *   peek()         — O(1):     inspect without removing
 *   isEmpty()      — O(1)
 *
 * Complexity:
 *   All heap operations: O(log N)
 *   Space: O(N) — N = number of ready tasks
 */
@Component
public class PriorityTaskScheduler {

    // Max-heap: Task.compareTo() is inverted (higher priority = dequeued first)
    private final PriorityQueue<Task> readyQueue = new PriorityQueue<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Adds a task to the ready queue.
     * The task's priority determines its position in the max-heap.
     */
    public void enqueue(Task task) {
        lock.lock();
        try {
            task.resetToReady();
            readyQueue.offer(task);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Enqueues a list of tasks atomically under one lock acquisition.
     * Used when multiple tasks become ready simultaneously after a completion.
     */
    public void enqueueAll(List<Task> tasks) {
        lock.lock();
        try {
            for (Task task : tasks) {
                task.resetToReady();
                readyQueue.offer(task);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes and returns the highest-priority ready task.
     * Returns null if queue is empty.
     * O(log N) heap extraction.
     */
    public Task dequeue() {
        lock.lock();
        try {
            Task task = readyQueue.poll();
            if (task != null) {
                task.transitionTo(TaskStatus.RUNNING);
            }
            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns (without removing) the highest-priority task.
     * O(1)
     */
    public Task peek() {
        lock.lock();
        try {
            return readyQueue.peek();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return readyQueue.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return readyQueue.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drains the queue and returns all pending tasks.
     * Used for job cancellation.
     */
    public List<Task> drainAll() {
        lock.lock();
        try {
            List<Task> all = new ArrayList<>(readyQueue);
            readyQueue.clear();
            return all;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            readyQueue.clear();
        } finally {
            lock.unlock();
        }
    }
}
