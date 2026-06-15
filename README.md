<div align="center">

# ⚙️ TaskForge

### Distributed DAG Task Scheduler & Executor

A production-grade job scheduling engine that models task dependencies as a **Directed Acyclic Graph**, executes them in topological order with parallel thread pools, and recovers from failures with exponential backoff retries.

![CI/CD](https://github.com/amangupta982/TaskForge/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-A31F34)
![Tests](https://img.shields.io/badge/Tests-26%20Passing-22C55E?logo=junit5&logoColor=white)

[Quick Start](#-quick-start) · [API Reference](#-api-reference) · [Architecture](#-architecture) · [Algorithms](#-algorithms--data-structures) · [Testing](#-testing-scenarios) · [Contributing](#-contributing)

</div>

---

## 📖 Overview

TaskForge is a **DAG-based job scheduling engine** built in Java that mirrors how systems like Apache Airflow, GitHub Actions, and AWS Step Functions work under the hood. It accepts a set of tasks with dependency rules, validates the graph, computes the optimal execution strategy, and runs tasks in parallel — automatically retrying failures with exponential backoff.

### Key Capabilities

- **Cycle Detection** — DFS 3-color algorithm rejects invalid circular dependencies at submission time
- **Topological Ordering** — Kahn's BFS algorithm computes a valid execution sequence
- **Critical Path Analysis** — Dynamic programming identifies the minimum possible completion time
- **Parallel Execution** — Independent tasks run concurrently via a configurable thread pool
- **Priority Scheduling** — Max-heap priority queue ensures high-priority tasks execute first
- **Fault Tolerance** — Exponential backoff retries (2s → 4s → 8s) with configurable max attempts
- **Real-time Monitoring** — Per-task status, progress percentage, and timing via REST API

---

## ⚡ Quick Start

### Prerequisites

| Requirement | Version |
|:--|:--|
| Java (JDK) | 21+ |
| Maven | 3.8+ |
| Docker | *(optional)* |

### Run Locally

```bash
git clone https://github.com/amangupta982/TaskForge.git
cd TaskForge
mvn spring-boot:run
```

### Run with Docker

```bash
docker build -t taskforge .
docker run -p 9090:9090 taskforge
```

### Verify It's Running

```bash
curl http://localhost:9090/actuator/health
# → {"status":"UP"}
```

### Try the Demo

```bash
# Launch the built-in CI/CD pipeline (6 tasks, parallel execution)
curl -X POST http://localhost:9090/api/jobs/demo

# Poll for live status (replace {jobId} with the returned ID)
curl http://localhost:9090/api/jobs/{jobId}/status
```

<details>
<summary><strong>📋 Sample Response</strong></summary>

```json
{
  "status": "COMPLETED",
  "progressPercent": 100.0,
  "criticalPath": ["t1", "t2", "t3", "t5", "t6"],
  "criticalPathDurationMs": 6500,
  "completedTasks": 6,
  "tasks": [...]
}
```

</details>

---

## 🏗 Architecture

```
                          ┌───────────────────────────┐
                          │   REST API (Spring Boot)   │
                          │       :9090                │
                          └─────────┬─────────────────┘
                                    │
                          ┌─────────▼─────────────────┐
                          │     JobOrchestrator        │
                          │   (master coordinator)     │
                          └─────────┬─────────────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
    ┌─────────▼──────────┐  ┌──────▼──────────┐  ┌───────▼──────────┐
    │    DAGValidator     │  │ CriticalPath    │  │   TaskExecutor   │
    │                     │  │ Finder          │  │                  │
    │  ┌───────────────┐  │  │  DP on topo-    │  │  ThreadPool +    │
    │  │ CycleDetector │  │  │  sorted DAG     │  │  CountDownLatch  │
    │  │ (DFS 3-color) │  │  │                 │  │                  │
    │  ├───────────────┤  │  └─────────────────┘  └────────┬─────────┘
    │  │ TopoSorter    │  │                                │
    │  │ (Kahn's BFS)  │  │                    ┌───────────┼───────────┐
    │  └───────────────┘  │                    │           │           │
    └─────────────────────┘           ┌────────▼───┐ ┌─────▼────┐ ┌───▼──────────┐
                                      │ Priority   │ │Dependency│ │ RetryManager │
                                      │ Scheduler  │ │ Tracker  │ │              │
                                      │ (max-heap) │ │(in-degree│ │ exp backoff  │
                                      │+Reentrant  │ │ map)     │ │ 2^n seconds  │
                                      │ Lock       │ │          │ │              │
                                      └────────────┘ └──────────┘ └──────────────┘
```

### Execution Flow

```
 Submit Job ──▶ Validate DAG ──▶ Compute Critical Path ──▶ Execute
                  │                     │                      │
                  ├─ Cycle detection    ├─ Topological sort    ├─ Enqueue ready tasks
                  ├─ Topological sort   ├─ DP longest path     ├─ Worker threads pick up
                  └─ Reference check    └─ Path backtrack      ├─ On success → unblock successors
                                                               ├─ On failure → exp backoff retry
                                                               └─ All done → mark COMPLETED
```

### Task Lifecycle

```
PENDING ──▶ READY ──▶ RUNNING ──┬──▶ DONE
                                │
                                └──▶ FAILED ──▶ (retry) ──▶ RUNNING
                                         │
                                         └──▶ DEAD (max retries exhausted)
```

---

## 🧮 Algorithms & Data Structures

| Algorithm / Structure | Time Complexity | Space | Purpose |
|:--|:--|:--|:--|
| DFS 3-color cycle detection | O(V + E) | O(V) | Reject cyclic DAGs at submission time |
| Kahn's topological sort | O(V + E) | O(V) | Compute a valid task execution ordering |
| DP critical path (longest path) | O(V + E) | O(V) | Find the minimum possible job completion time |
| Max-heap `PriorityQueue` | O(log N) insert/poll | O(N) | Priority-based task scheduling |
| `ThreadPoolExecutor` | O(1) dispatch | — | Parallel execution of independent tasks |
| `CountDownLatch` | O(1) per op | O(1) | Synchronize job completion across threads |
| `ConcurrentHashMap` + `AtomicInteger` | O(1) amortized | O(V) | Thread-safe in-degree tracking |
| Exponential backoff | O(1) | O(1) | Retry failed tasks: 2s → 4s → 8s → DEAD |

---

## 📡 API Reference

Base URL: `http://localhost:9090`

### Endpoints

| Method | Endpoint | Description |
|:--|:--|:--|
| `POST` | `/api/jobs` | Submit a new job — validates DAG, computes critical path |
| `POST` | `/api/jobs/{id}/execute` | Start execution of a validated job |
| `POST` | `/api/jobs/{id}/execute?failureRate=0.3` | Execute with simulated failures (for testing retries) |
| `GET` | `/api/jobs/{id}/status` | Real-time status — per-task progress, critical path, timing |
| `GET` | `/api/jobs/{id}/dag` | DAG adjacency list + node metadata (for visualization) |
| `GET` | `/api/jobs` | List all submitted jobs |
| `DELETE` | `/api/jobs/{id}` | Cancel a running job |
| `POST` | `/api/jobs/demo` | Run the built-in CI/CD pipeline demo |

### Submit a Job

```http
POST /api/jobs
Content-Type: application/json
```

```json
{
  "name": "My Pipeline",
  "tasks": [
    { "id": "a", "name": "Task A", "priority": 10, "durationMs": 500, "maxRetries": 2 },
    { "id": "b", "name": "Task B", "priority": 8,  "durationMs": 1000, "maxRetries": 2 }
  ],
  "dependencies": [
    { "from": "a", "to": "b" }
  ]
}
```

<details>
<summary><strong>Response — 201 Created</strong></summary>

```json
{
  "jobId": "abc-123",
  "jobName": "My Pipeline",
  "status": "VALIDATED",
  "totalTasks": 2,
  "criticalPath": ["a", "b"],
  "criticalPathDurationMs": 1500,
  "message": "Job created and validated. POST /api/jobs/abc-123/execute to run."
}
```

</details>

---

## 🧪 Testing Scenarios

### 1. Run the Demo Pipeline

```bash
curl -X POST http://localhost:9090/api/jobs/demo
curl http://localhost:9090/api/jobs/{jobId}/status
```

### 2. Test Retry with Simulated Failures

```bash
# 30% of tasks will randomly fail — watch exponential backoff in logs
curl -X POST "http://localhost:9090/api/jobs/{jobId}/execute?failureRate=0.3"
```

### 3. Cycle Detection (should return 400)

```bash
curl -X POST http://localhost:9090/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bad Job",
    "tasks": [
      {"id":"a","name":"A","priority":5,"durationMs":100,"maxRetries":1},
      {"id":"b","name":"B","priority":5,"durationMs":100,"maxRetries":1}
    ],
    "dependencies": [{"from":"a","to":"b"},{"from":"b","to":"a"}]
  }'
```

> Expected: `400 Bad Request` — `Cycle detected: [a, b, a]`

### 4. Large-Scale Pipeline (25 tasks)

<details>
<summary><strong>Expand: 25-task enterprise CI/CD pipeline</strong></summary>

```bash
curl -X POST http://localhost:9090/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Enterprise CI/CD — 25 Tasks",
    "tasks": [
      {"id":"checkout","name":"Git Checkout","priority":10,"durationMs":300,"maxRetries":2},
      {"id":"deps","name":"Install Dependencies","priority":9,"durationMs":800,"maxRetries":2},
      {"id":"compile","name":"Compile Sources","priority":9,"durationMs":1200,"maxRetries":2},
      {"id":"unit1","name":"Unit Test Auth","priority":8,"durationMs":600,"maxRetries":3},
      {"id":"unit2","name":"Unit Test Payment","priority":8,"durationMs":700,"maxRetries":3},
      {"id":"unit3","name":"Unit Test User","priority":8,"durationMs":500,"maxRetries":3},
      {"id":"unit4","name":"Unit Test Orders","priority":8,"durationMs":650,"maxRetries":3},
      {"id":"unit5","name":"Unit Test Inventory","priority":8,"durationMs":550,"maxRetries":3},
      {"id":"int1","name":"Integration Test DB","priority":8,"durationMs":900,"maxRetries":2},
      {"id":"int2","name":"Integration Test API","priority":8,"durationMs":1000,"maxRetries":2},
      {"id":"lint","name":"Code Lint","priority":6,"durationMs":400,"maxRetries":1},
      {"id":"security","name":"Security Scan","priority":9,"durationMs":1100,"maxRetries":2},
      {"id":"sonar","name":"SonarQube Analysis","priority":7,"durationMs":900,"maxRetries":2},
      {"id":"docker_build","name":"Docker Build","priority":9,"durationMs":1500,"maxRetries":2},
      {"id":"docker_scan","name":"Docker Scan","priority":8,"durationMs":800,"maxRetries":2},
      {"id":"docker_push","name":"Push to Registry","priority":8,"durationMs":600,"maxRetries":3},
      {"id":"deploy_dev","name":"Deploy to Dev","priority":8,"durationMs":700,"maxRetries":3},
      {"id":"smoke_dev","name":"Smoke Test Dev","priority":8,"durationMs":400,"maxRetries":3},
      {"id":"deploy_staging","name":"Deploy to Staging","priority":9,"durationMs":900,"maxRetries":3},
      {"id":"smoke_stg","name":"Smoke Test Staging","priority":9,"durationMs":400,"maxRetries":3},
      {"id":"perf_test","name":"Performance Test","priority":7,"durationMs":1200,"maxRetries":2},
      {"id":"qa_approval","name":"QA Sign-off","priority":10,"durationMs":200,"maxRetries":1},
      {"id":"deploy_prod","name":"Deploy to Production","priority":10,"durationMs":1200,"maxRetries":3},
      {"id":"smoke_prod","name":"Smoke Test Prod","priority":10,"durationMs":500,"maxRetries":3},
      {"id":"notify","name":"Notify Team","priority":5,"durationMs":100,"maxRetries":1}
    ],
    "dependencies": [
      {"from":"checkout","to":"deps"},{"from":"deps","to":"compile"},
      {"from":"compile","to":"unit1"},{"from":"compile","to":"unit2"},
      {"from":"compile","to":"unit3"},{"from":"compile","to":"unit4"},
      {"from":"compile","to":"unit5"},{"from":"compile","to":"int1"},
      {"from":"compile","to":"int2"},{"from":"compile","to":"lint"},
      {"from":"compile","to":"security"},
      {"from":"unit1","to":"sonar"},{"from":"unit2","to":"sonar"},
      {"from":"unit3","to":"sonar"},{"from":"int1","to":"sonar"},
      {"from":"sonar","to":"docker_build"},{"from":"security","to":"docker_build"},
      {"from":"lint","to":"docker_build"},
      {"from":"docker_build","to":"docker_scan"},
      {"from":"docker_scan","to":"docker_push"},
      {"from":"docker_push","to":"deploy_dev"},
      {"from":"deploy_dev","to":"smoke_dev"},
      {"from":"smoke_dev","to":"deploy_staging"},
      {"from":"deploy_staging","to":"smoke_stg"},
      {"from":"deploy_staging","to":"perf_test"},
      {"from":"smoke_stg","to":"qa_approval"},
      {"from":"perf_test","to":"qa_approval"},
      {"from":"qa_approval","to":"deploy_prod"},
      {"from":"deploy_prod","to":"smoke_prod"},
      {"from":"smoke_prod","to":"notify"}
    ]
  }'
```

</details>

### 5. Run Unit Tests

```bash
mvn test
```

> **26 tests** across 4 test classes — covers cycle detection, topological sorting, critical path computation, and end-to-end job execution.

---

## 📁 Project Structure

```
src/main/java/com/taskforge/
│
├── model/                              # Domain models
│   ├── Task.java                       # Graph node — AtomicReference status, backoff delay
│   ├── Job.java                        # Job container — ConcurrentHashMap task registry
│   ├── DAG.java                        # Adjacency list — in-degree map for Kahn's sort
│   ├── TaskStatus.java                 # PENDING → READY → RUNNING → DONE / FAILED / DEAD
│   └── JobStatus.java                  # CREATED → VALIDATED → RUNNING → COMPLETED / FAILED
│
├── algorithm/                          # Core graph algorithms
│   ├── CycleDetector.java              # DFS 3-color — WHITE / GRAY / BLACK marking
│   ├── TopologicalSorter.java          # Kahn's BFS — in-degree queue processing
│   └── CriticalPathFinder.java         # DP longest path — earliest start times per task
│
├── scheduler/                          # Scheduling & resilience
│   ├── PriorityTaskScheduler.java      # Max-heap PriorityQueue + ReentrantLock
│   ├── DependencyTracker.java          # ConcurrentHashMap + AtomicInteger in-degree
│   └── RetryManager.java              # ScheduledExecutorService + 2^n backoff
│
├── executor/                           # Thread pool execution
│   ├── TaskExecutor.java               # ThreadPoolExecutor + CountDownLatch orchestration
│   └── TaskWorker.java                 # Runnable per task — success/failure callbacks
│
├── service/                            # Business logic
│   ├── JobOrchestrator.java            # @Async coordinator — validate → analyse → execute
│   ├── DAGValidator.java               # Facade: cycle check + topo sort + reference check
│   └── JobService.java                 # CRUD + in-memory ConcurrentHashMap job registry
│
├── controller/
│   └── JobController.java              # 8 REST endpoints
│
├── dto/                                # Request/response DTOs
│   ├── JobSubmitRequest.java           # Validated job submission payload
│   ├── JobStatusResponse.java          # Status response with task details
│   └── DAGResponse.java               # DAG visualization data
│
└── config/
    ├── AsyncConfig.java                # Spring thread pool — core=4, max=10
    └── GlobalExceptionHandler.java     # Clean JSON error responses
```

---

## 🔄 CI/CD Pipeline

Every push to `main` triggers the GitHub Actions workflow:

```
Push to main
     │
     ▼
 Build & Test ─────────────────────────────┐
     │                                     │
     ▼ (on success, in parallel)           │
  ┌──────────────────────┐  ┌──────────────▼──────────┐
  │  Code Quality Check  │  │  Docker Build & Verify  │
  └──────────────────────┘  └─────────────────────────┘
```

| Job | Description |
|:--|:--|
| **Build & Test** | Compiles 23 source files, runs 26 tests, uploads JAR artifact |
| **Code Quality** | Verifies zero compilation warnings, confirms all tests pass |
| **Docker Build** | Builds multi-stage image, starts container, validates `/actuator/health` |

---

## 🐳 Docker

The project uses a **multi-stage Dockerfile** for optimized image size:

| Stage | Base Image | Purpose |
|:--|:--|:--|
| Builder | `maven:3.9.5-eclipse-temurin-21` | Compile and package |
| Runtime | `eclipse-temurin:21-jre-alpine` | Minimal runtime (~200 MB) |

The container includes a built-in healthcheck that polls `/actuator/health` every 30 seconds.

```bash
# Build
docker build -t taskforge .

# Run
docker run -p 9090:9090 taskforge

# Run with custom failure rate for testing
docker run -p 9090:9090 -e FAILURE_RATE=0.3 taskforge
```

---

## ⚙️ Configuration

Key settings in [`application.yml`](src/main/resources/application.yml):

| Property | Default | Description |
|:--|:--|:--|
| `server.port` | `9090` | HTTP server port |
| `logging.level.com.taskforge` | `DEBUG` | Application log level |
| Thread pool (core) | `4` | Minimum async worker threads |
| Thread pool (max) | `10` | Maximum async worker threads |
| Execution timeout | `10 min` | Maximum job execution duration |

---

## 📌 How It Works

You submit a list of tasks and their dependency rules. TaskForge handles the rest:

1. **Validates** — DFS 3-color marks each node WHITE → GRAY → BLACK. If a GRAY node is revisited, a back-edge (cycle) is detected and the job is rejected instantly with the cycle path.

2. **Plans** — Kahn's algorithm peels off zero-in-degree nodes layer by layer to produce the topological ordering. Then DP computes `dp[v] = max(dp[u] + duration[v])` for all predecessors to find the critical path — the longest chain that defines minimum completion time.

3. **Executes** — Tasks with zero pending dependencies enter a max-heap priority queue (guarded by `ReentrantLock`). Worker threads dequeue and execute them. When a task completes, `DependencyTracker` decrements successors' in-degree counters atomically — any that hit zero are enqueued immediately.

4. **Recovers** — Failed tasks are retried with exponential backoff via `ScheduledExecutorService`. Delay doubles each attempt (2s → 4s → 8s). After exhausting `maxRetries`, the task is marked `DEAD` and the `CountDownLatch` counts down to prevent deadlock.

---

## 🤝 Contributing

Contributions are welcome! Please read the **[Contributing Guide](CONTRIBUTING.md)** for details on our development workflow, commit conventions, and code style.

```bash
# Quick start for contributors
git clone https://github.com/<your-username>/TaskForge.git
cd TaskForge
mvn test   # ensure everything passes
```

---

## 📝 License

This project is licensed under the [MIT License](LICENSE) — free to use, fork, and build on.

---

<div align="center">

**Built with** ☕ **Java 21 · Spring Boot 3.2 · Graph Algorithms**

</div>