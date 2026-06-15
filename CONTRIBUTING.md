# Contributing to TaskForge

Thank you for considering contributing to TaskForge! Whether it's a bug fix, a new feature, improved documentation, or a test — every contribution makes this project better.

---

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Making Changes](#making-changes)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Reporting Issues](#reporting-issues)
- [Code Style](#code-style)

---

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment. Be kind, constructive, and professional in all interactions.

---

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/TaskForge.git
   cd TaskForge
   ```
3. **Add the upstream remote** to stay in sync:
   ```bash
   git remote add upstream https://github.com/amangupta982/TaskForge.git
   ```
4. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

---

## Development Setup

### Prerequisites

| Tool | Version | Check |
|:--|:--|:--|
| Java (JDK) | 21+ | `java --version` |
| Maven | 3.8+ | `mvn --version` |
| Docker | *(optional)* | `docker --version` |
| Git | 2.x+ | `git --version` |

### Build & Run

```bash
# Compile the project
mvn clean compile

# Run the application
mvn spring-boot:run

# Run all tests
mvn test

# Package the JAR
mvn clean package -DskipTests
```

### Docker (optional)

```bash
docker build -t taskforge .
docker run -p 9090:9090 taskforge
```

### Verify

```bash
# Health check
curl http://localhost:9090/actuator/health

# Run the demo pipeline
curl -X POST http://localhost:9090/api/jobs/demo
```

---

## Project Structure

```
src/main/java/com/taskforge/
├── algorithm/       # Graph algorithms (cycle detection, topo sort, critical path)
├── config/          # Spring configuration (async, exception handling)
├── controller/      # REST API endpoints
├── dto/             # Request/response data transfer objects
├── executor/        # Thread pool execution engine
├── model/           # Domain models (Task, Job, DAG, enums)
├── scheduler/       # Priority scheduling, dependency tracking, retry logic
└── service/         # Business logic and orchestration

src/test/java/com/taskforge/
├── CycleDetectorTest.java
├── TopologicalSorterTest.java
├── CriticalPathFinderTest.java
└── JobExecutionIntegrationTest.java
```

---

## Making Changes

### What to Contribute

| Type | Examples |
|:--|:--|
| 🐛 **Bug fixes** | Fix race conditions, handle edge cases, correct error messages |
| ✨ **Features** | New scheduling strategies, persistence layer, web dashboard |
| 📝 **Documentation** | Improve README, add Javadoc, write tutorials |
| 🧪 **Tests** | Add unit tests, integration tests, stress tests |
| ♻️ **Refactoring** | Improve code clarity, reduce duplication, optimize performance |
| 🔧 **Infrastructure** | CI/CD improvements, Docker optimizations, dependency updates |

### Before You Start

- Check [existing issues](https://github.com/amangupta982/TaskForge/issues) to avoid duplicate work
- For **large changes**, open an issue first to discuss the approach
- For **small fixes**, feel free to submit a PR directly

---

## Commit Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <short summary>

<optional body with more detail>
```

### Types

| Type | When to Use |
|:--|:--|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `test` | Adding or updating tests |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvement |
| `ci` | CI/CD pipeline changes |
| `chore` | Dependency updates, config changes |

### Examples

```bash
feat(scheduler): add weighted round-robin scheduling strategy
fix(executor): resolve race condition in CountDownLatch decrement
docs(readme): add architecture diagram for retry flow
test(algorithm): add edge case tests for disconnected DAG components
```

---

## Pull Request Process

1. **Sync with upstream** before pushing:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Ensure all tests pass**:
   ```bash
   mvn test
   ```

3. **Push** your branch:
   ```bash
   git push origin feature/your-feature-name
   ```

4. **Open a Pull Request** against `main` on GitHub

5. **Fill out the PR description**:
   - What does this PR do?
   - How was it tested?
   - Any breaking changes?

### PR Checklist

- [ ] Code compiles without warnings (`mvn clean compile`)
- [ ] All existing tests pass (`mvn test`)
- [ ] New code has corresponding tests (if applicable)
- [ ] Comments and Javadoc are updated where needed
- [ ] Commit messages follow [Conventional Commits](#commit-guidelines)
- [ ] No unrelated changes are bundled in the PR

---

## Reporting Issues

When opening an issue, please include:

### For Bugs

- **Description**: What happened vs. what you expected
- **Steps to reproduce**: `curl` commands, JSON payloads, or test code
- **Environment**: Java version, OS, Docker version (if applicable)
- **Logs**: Relevant stack traces or application logs

### For Feature Requests

- **Problem**: What limitation or pain point are you addressing?
- **Proposed solution**: How would you like it to work?
- **Alternatives**: Other approaches you considered

---

## Code Style

### General Guidelines

- **Java 21** — use modern language features (records, pattern matching, sealed classes) where appropriate
- **Spring conventions** — use `@Autowired` constructor injection for new code, keep controllers thin
- **Thread safety** — all shared mutable state must use `java.util.concurrent` primitives
- **Logging** — use SLF4J (`LoggerFactory.getLogger`) at appropriate levels:
  - `DEBUG` for internal state transitions
  - `INFO` for lifecycle events (task started, completed)
  - `WARN` for recoverable failures (retry scheduled)
  - `ERROR` for terminal failures (task DEAD, job failed)

### Naming Conventions

| Element | Convention | Example |
|:--|:--|:--|
| Classes | PascalCase | `CriticalPathFinder` |
| Methods | camelCase | `findCriticalPath()` |
| Constants | UPPER_SNAKE | `THREAD_POOL_SIZE` |
| Packages | lowercase | `com.taskforge.algorithm` |
| Test classes | `*Test` suffix | `CycleDetectorTest` |

### Testing

- Write **unit tests** for algorithms and pure logic
- Write **integration tests** for end-to-end job execution flows
- Use **descriptive test names** that explain the scenario:
  ```java
  @Test
  void shouldDetectCycleInTwoNodeGraph() { ... }
  
  @Test
  void shouldRejectJobWithMissingTaskReference() { ... }
  ```

---

## 💡 Ideas for Contributions

Looking for inspiration? Here are some areas where help is welcome:

- [ ] **Persistence layer** — save jobs to a database (PostgreSQL, H2) instead of in-memory
- [ ] **Web dashboard** — visualize DAGs and job progress in real-time (D3.js or Vis.js)
- [ ] **Webhook notifications** — notify external systems on job completion or failure
- [ ] **Cron scheduling** — schedule jobs to run on a recurring basis
- [ ] **Task timeout** — kill tasks that exceed a configurable duration
- [ ] **Metrics & monitoring** — expose Prometheus metrics via Micrometer
- [ ] **Rate limiting** — throttle API requests to prevent abuse
- [ ] **Job history & audit log** — track execution history with timestamps

---

Thank you for helping make TaskForge better! 🚀
