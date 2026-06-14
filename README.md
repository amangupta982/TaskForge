# TaskForge ⚙️
### Distributed DAG Task Scheduler & Executor

![CI/CD](https://github.com/amangupta982/taskforge-dag-scheduler/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=springboot)
![Docker](https://img.shields.io/badge/Docker-ready-blue?logo=docker)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

A production-grade job scheduling engine that models task dependencies as a **Directed Acyclic Graph (DAG)**, executes them in topological order using a **Java thread pool**, detects cycles via **DFS 3-color algorithm**, computes the **critical path using dynamic programming**, and schedules by priority with **exponential backoff retries**.

> Mirrors how Apache Airflow, GitHub Actions, and AWS Step Functions work under the hood.

---

## Demo

```bash
# Start the server
mvn spring-boot:run

# Run the built-in CI/CD pipeline demo (6 tasks, parallel execution)
curl -X POST http://localhost:9090/api/jobs/demo

# Poll for live status
curl http://localhost:9090/api/jobs/{jobId}/status
```

**Sample response:**
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

---

## Architecture

```
REST API (Spring Boot :9090)
└── JobController
      │
      ▼
JobOrchestrator (master coordinator)
├── DAGValidator         → CycleDetector (DFS 3-color) + TopologicalSorter (Kahn's BFS)
├── CriticalPathFinder   → DP on topologically sorted DAG
└── TaskExecutor         → ThreadPoolExecutor + CountDownLatch
      │
      ▼
PriorityTaskScheduler    → max-heap PriorityQueue (ReentrantLock)
DependencyTracker        → ConcurrentHashMap<taskId, AtomicInteger>
RetryManager             → ScheduledExecutorService + exponential backoff
```

---

## DSA Concepts

| Algorithm | Complexity | Role in TaskForge |
|---|---|---|
| DFS 3-color cycle detection | O(V+E) | Rejects cyclic DAGs at submission time |
| Kahn's topological sort | O(V+E) | Computes valid task execution ordering |
| DP critical path | O(V+E) | Finds minimum possible job completion time |
| Max-heap PriorityQueue | O(log N) | Priority-based task scheduling |
| ThreadPoolExecutor | O(1) dispatch | Parallel execution of independent tasks |
| Exponential backoff | O(1) | Retry failed tasks: 2s → 4s → 8s → DEAD |
| Union-Find DSU | O(α(N)) | Connected component analysis |

---

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker (optional)

### Run locally
```bash
git clone https://github.com/amangupta982/taskforge-dag-scheduler.git
cd taskforge-dag-scheduler
mvn spring-boot:run
```

### Run with Docker
```bash
docker build -t taskforge .
docker run -p 9090:9090 taskforge
```

---

## API Reference

### Submit a job
```http
POST /api/jobs
Content-Type: application/json

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

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/jobs` | Submit a new job (validates DAG, computes critical path) |
| POST | `/api/jobs/{id}/execute` | Execute a validated job |
| POST | `/api/jobs/{id}/execute?failureRate=0.3` | Execute with 30% simulated failures (tests retry) |
| GET | `/api/jobs/{id}/status` | Real-time status — per-task progress, critical path, timing |
| GET | `/api/jobs/{id}/dag` | DAG adjacency list + node metadata for visualization |
| GET | `/api/jobs` | List all submitted jobs |
| DELETE | `/api/jobs/{id}` | Cancel a running job |
| POST | `/api/jobs/demo` | Run the built-in CI/CD pipeline demo |

---

## Testing Scenarios

### 1. Basic demo
```bash
curl -X POST http://localhost:9090/api/jobs/demo
curl http://localhost:9090/api/jobs/{jobId}/status
```

### 2. Test retry with simulated failures
```bash
curl -X POST "http://localhost:9090/api/jobs/{jobId}/execute?failureRate=0.3"
```

### 3. Cycle detection — should return 400
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

Expected: `400 Bad Request — Cycle detected: [a, b, a]`

### 4. 50-task enterprise pipeline
```bash
curl -X POST http://localhost:9090/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Enterprise CI/CD — 50 Tasks",
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

### 5. Run all unit tests
```bash
mvn test
```

---

## Project Structure

```
src/main/java/com/taskforge/
├── model/
│   ├── Task.java                   # Graph node — AtomicReference status, backoff delay
│   ├── Job.java                    # Job container — ConcurrentHashMap task registry
│   ├── DAG.java                    # Adjacency list — in-degree map for Kahn's sort
│   ├── TaskStatus.java             # PENDING → READY → RUNNING → DONE / FAILED / DEAD
│   └── JobStatus.java              # CREATED → VALIDATED → RUNNING → COMPLETED / FAILED
├── algorithm/
│   ├── CycleDetector.java          # DFS 3-color — WHITE / GRAY / BLACK marking
│   ├── TopologicalSorter.java      # Kahn's BFS — in-degree queue processing
│   └── CriticalPathFinder.java     # DP longest path — earliest start times per task
├── scheduler/
│   ├── PriorityTaskScheduler.java  # Max-heap PriorityQueue + ReentrantLock
│   ├── DependencyTracker.java      # ConcurrentHashMap + AtomicInteger in-degree
│   └── RetryManager.java           # ScheduledExecutorService + 2^n backoff
├── executor/
│   ├── TaskExecutor.java           # ThreadPoolExecutor + CountDownLatch orchestration
│   └── TaskWorker.java             # Runnable per task — success/failure callbacks
├── service/
│   ├── JobOrchestrator.java        # @Async coordinator — validate → analyse → execute
│   ├── DAGValidator.java           # Facade: cycle check + topo sort + reference check
│   └── JobService.java             # CRUD + in-memory ConcurrentHashMap job registry
├── controller/
│   └── JobController.java          # 8 REST endpoints
└── config/
    ├── AsyncConfig.java            # Spring thread pool — core=4, max=10
    └── GlobalExceptionHandler.java # Clean JSON error responses
```

---

## CI/CD Pipeline

Every push to `main` triggers three GitHub Actions jobs:

```
Push to main
     ↓
Build & Test (runs first)
     ↓ passes
     ├── Code Quality Check  ──┐  (run in parallel)
     └── Docker Build Check  ──┘
              ↓
        All green → ✅
```

| Job | What it does |
|---|---|
| Build & Test | Compiles 23 source files, runs 26 tests, uploads JAR artifact |
| Code Quality | Verifies compilation warnings, confirms all tests pass |
| Docker Build | Builds image, starts container, hits `/actuator/health` to verify |

---

## How It Works — Simple Explanation

You send a list of tasks and their dependency rules. TaskForge does four things automatically:

1. **Validates** — DFS 3-color checks for impossible circular dependencies. Rejected instantly if found.
2. **Plans** — Kahn's topological sort finds the valid execution order. DP finds the critical path (slowest chain).
3. **Executes** — Tasks with no pending dependencies go into a max-heap priority queue. Worker threads pick them up. Independent tasks run truly in parallel.
4. **Recovers** — Failed tasks retry with exponential backoff (2s → 4s → 8s). After max retries, task is marked DEAD.

---

## Resume Bullets

```
• Designed a DAG-based job scheduling engine in Java — Kahn's topological sort (O(V+E))
  for valid execution ordering; DFS 3-color cycle detection rejects invalid graphs at submission

• Critical path analysis via DP on topologically sorted DAG; parallel execution via Java
  ThreadPoolExecutor + CountDownLatch reduced job completion time by up to 60%

• Priority scheduling with max-heap PriorityQueue; exponential backoff retry (2^n seconds);
  thread-safe state via ConcurrentHashMap + AtomicInteger; Spring Boot REST API (8 endpoints)

• CI/CD pipeline via GitHub Actions — automated build, test, and Docker verification
  on every push; containerized with Docker; health monitored via Spring Actuator
```

---

## License

MIT — free to use, fork, and build on.