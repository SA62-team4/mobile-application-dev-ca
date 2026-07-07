# Docker Configuration Changes

This document records every change made to existing Docker files and every new
file created during the DevSecOps containerisation session.

---

## Files Changed (Existing Files Improved)

---

### `spring-backend/Dockerfile`

#### Original File (before changes)

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### New File (after changes)

```dockerfile
# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

#### Line-by-Line Diff Explanation

| Location | What Changed | Why |
|---|---|---|
| Runtime stage name | `FROM eclipse-temurin:17-jre` → `FROM eclipse-temurin:17-jre AS runtime` | Naming the stage makes Dockerfiles with multiple stages easier to read and allows future `COPY --from=runtime` references. |
| **NEW BLOCK** — curl install | Added `RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*` | The `HEALTHCHECK` below uses `curl` to probe `/actuator/health`. The base `eclipse-temurin:17-jre` image (Ubuntu 22.04) does not include `curl` by default. `--no-install-recommends` keeps the layer small. Cleaning `/var/lib/apt/lists/*` in the same `RUN` instruction removes the package index from the layer, saving ~30 MB. |
| **NEW BLOCK** — non-root user | Added `RUN groupadd --system appgroup && useradd --system --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser` | Running as root inside a container is a security risk. If an attacker exploits the application, they get root inside the container. A dedicated system user (`appuser`) with no home directory and no login shell limits what an attacker can do. |
| `COPY --from=build` | Added `--chown=appuser:appgroup` | Without `--chown`, the file is owned by root. Because we switch to `appuser` before `ENTRYPOINT`, the process must be able to read `app.jar`. Setting ownership at copy time is more efficient than a separate `RUN chown` step (which would create an extra layer). |
| **NEW LINE** — `USER appuser` | Added `USER appuser` after the `COPY` | This instruction drops all root privileges permanently for every instruction that follows it, including `ENTRYPOINT`. The JVM process runs as `appuser`, not root. |
| **NEW BLOCK** — `HEALTHCHECK` | Added complete `HEALTHCHECK` directive | Docker health checks let Compose wait for a container to be truly ready before starting dependent containers. Without a health check, `depends_on: condition: service_healthy` in `docker-compose.yml` cannot work. Parameters: `--interval=15s` (check every 15 s), `--timeout=5s` (fail if no response within 5 s), `--start-period=60s` (ignore failures for the first 60 s while Spring Boot starts up), `--retries=5` (mark unhealthy after 5 consecutive failures). |
| `ENTRYPOINT` | Added `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0` JVM flags | **Problem:** Without these flags, the JVM reads the host machine's total RAM (e.g. 32 GB) and sizes its heap accordingly. Inside a container limited to 512 MB, the JVM will try to allocate a multi-GB heap and be killed by the OS with `OOMKilled`. **Fix:** `UseContainerSupport` (enabled by default in Java 11+ but explicit here for clarity) makes the JVM read the cgroup memory limit set by Docker. `MaxRAMPercentage=75.0` caps the heap at 75 % of that limit, leaving 25 % for OS threads, off-heap memory, and the JVM itself. |

---

### `python-ai-service/Dockerfile`

#### Original File (before changes)

```dockerfile
FROM python:3.12-slim

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y --no-install-recommends build-essential && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

#### New File (after changes)

```dockerfile
FROM python:3.12-slim AS runtime
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY --chown=appuser:appgroup app ./app

USER appuser

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

#### Line-by-Line Diff Explanation

| Location | What Changed | Why |
|---|---|---|
| `FROM` line | Added `AS runtime` stage name | Consistency with the Spring Boot Dockerfile convention and allows future multi-stage extension if a build step is ever added. |
| **NEW BLOCK** — non-root user | Added `RUN groupadd --system appgroup && useradd --system ...` **after** the `apt-get` block | Same security rationale as the Java Dockerfile. The user is created **after** `apt-get` so that `apt-get` still runs as root (it needs root to install packages), but the application process runs as `appuser`. |
| `COPY app ./app` | Added `--chown=appuser:appgroup` | The `appuser` process must be able to read the Python source files. Setting ownership at copy time avoids an extra `RUN chown` layer. |
| **NEW LINE** — `USER appuser` | Added before `EXPOSE` | Permanently drops to `appuser` for all subsequent instructions and the final `CMD`. |
| **NEW BLOCK** — `HEALTHCHECK` | Added with `--start-period=30s` | FastAPI starts quickly but ChromaDB needs time to open its index files. `30s` gives it a grace period. The check uses Python's standard-library `urllib.request` — no extra packages needed. `--retries=5` means Docker tries 5 times before marking the container unhealthy, giving the app time to recover from transient startup delays. |

---

### `docker-compose.yml`

#### Original File (before changes)

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE:-wellness_app}
      MYSQL_USER: ${MYSQL_USER:-wellness_user}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-change_me}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-change_me_root}
    ports:
      - "${MYSQL_HOST_PORT:-3307}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 10

  ollama:
    image: ollama/ollama:latest
    ports:
      - "${OLLAMA_HOST_PORT:-11434}:11434"
    volumes:
      - ollama-data:/root/.ollama

  python-ai-service:
    build: ./python-ai-service
    environment:
      OLLAMA_BASE_URL: ${OLLAMA_BASE_URL:-http://ollama:11434}
      OLLAMA_GENERATION_MODEL: ${OLLAMA_GENERATION_MODEL:-qwen2.5:1.5b}
      OLLAMA_EMBEDDING_MODEL: ${OLLAMA_EMBEDDING_MODEL:-nomic-embed-text}
      CHROMA_PERSIST_DIR: ${CHROMA_PERSIST_DIR:-/data/chroma}
      KNOWLEDGE_BASE_DIR: /app/rag-knowledge-base
      BACKEND_BASE_URL: http://spring-backend:8080
      INTERNAL_SERVICE_TOKEN: ${INTERNAL_SERVICE_TOKEN:-replace_with_internal_token}
    ports:
      - "${AI_SERVICE_HOST_PORT:-8000}:8000"
    volumes:
      - ./rag-knowledge-base:/app/rag-knowledge-base:ro
      - chroma-data:/data/chroma
    depends_on:
      - ollama

  spring-backend:
    build: ./spring-backend
    environment:
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:mysql://mysql:3306/wellness_app}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:-wellness_user}
      SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD:-change_me}
      JWT_SECRET: ${JWT_SECRET:-replace_with_a_long_random_secret_at_least_32_chars}
      JWT_EXPIRY_SECONDS: ${JWT_EXPIRY_SECONDS:-86400}
      AI_SERVICE_URL: ${AI_SERVICE_URL:-http://python-ai-service:8000}
      INTERNAL_SERVICE_TOKEN: ${INTERNAL_SERVICE_TOKEN:-replace_with_internal_token}
    ports:
      - "${SPRING_HOST_PORT:-8080}:8080"
    depends_on:
      mysql:
        condition: service_healthy
      python-ai-service:
        condition: service_started

  adminer:
    image: adminer:latest
    ports:
      - "${ADMINER_HOST_PORT:-8081}:8080"
    depends_on:
      - mysql

volumes:
  mysql-data:
  ollama-data:
  chroma-data:
```

#### Line-by-Line Diff Explanation (by service)

##### `mysql` service

| Field | Before | After | Why |
|---|---|---|---|
| `networks` | Missing — not attached to any named network | `networks: - wellness-net` added | Without explicit network assignment, Docker Compose creates an auto-named default network. Naming the network `wellness-net` makes it visible in `docker network ls`, lets external tools attach to it, and is good documentation. Every service must be on this network for DNS resolution to work. |
| `healthcheck.test` | `["CMD", "mysqladmin", "ping", "-h", "localhost"]` | `["CMD", "mysqladmin", "ping", "-h", "localhost", "--silent"]` | Added `--silent` to suppress the `mysqladmin` banner text from appearing in health-check logs. Functionally identical; cleaner output. |
| `healthcheck.start_period` | Missing | `start_period: 30s` | Without `start_period`, Docker begins counting failures from the very first second. MySQL takes 10–30 seconds to initialise its data directory on first boot. Adding `start_period: 30s` tells Docker to ignore health-check failures during the first 30 seconds, preventing false `unhealthy` reports during normal startup. |
| `restart` | Missing | `restart: unless-stopped` | If MySQL crashes, Docker will automatically restart it. `unless-stopped` means "restart always, except when the user explicitly ran `docker compose stop`". This improves resilience. |
| `deploy.resources.limits.memory` | Missing | `memory: 512m` | Without a memory limit, a runaway MySQL instance (e.g. a large query) can consume all available RAM on the host and crash other containers. 512 MB is sufficient for development workloads with this schema. |

##### `ollama` service

| Field | Before | After | Why |
|---|---|---|---|
| `networks` | Missing | `networks: - wellness-net` added | Same rationale as `mysql` — all services must share the same network to use DNS-based service names. |
| `restart` | Missing | `restart: unless-stopped` | If Ollama crashes during a generation, it will be restarted automatically. |
| `deploy.resources.limits.memory` | Missing | `memory: 4g` | Ollama loads the full model weights into memory. `qwen2.5:1.5b` requires approximately 1–2 GB. Setting 4 GB gives headroom for multiple concurrent requests. Without this limit, Ollama could consume all available host RAM. |

##### `python-ai-service` service

| Field | Before | After | Why |
|---|---|---|---|
| `build` format | `build: ./python-ai-service` (short form) | Expanded to `build: context: ./python-ai-service` (long form) | The long form is more explicit and allows future addition of `dockerfile:`, `args:`, `target:`, or `cache_from:` fields without restructuring. Functionally equivalent. |
| `networks` | Missing | `networks: - wellness-net` added | Same rationale as all other services. |
| `depends_on` | `- ollama` (simple list, no condition) | `ollama: condition: service_started` | Explicit condition form is required when mixing `service_started` and `service_healthy` conditions in the same file. The result is identical to the original for `ollama` (no wait for healthiness). |
| `healthcheck` | **Entirely missing** | Added full `healthcheck` block with `test`, `interval`, `timeout`, `retries`, `start_period` | Without a health check on `python-ai-service`, the `spring-backend` service (which depends on it) had no way to know if it was actually ready to serve requests. `condition: service_healthy` in `spring-backend` depends on this health check existing. |
| `restart` | Missing | `restart: unless-stopped` | Automatic recovery if FastAPI crashes. |
| `deploy.resources.limits.memory` | Missing | `memory: 1g` | ChromaDB keeps its index partially in memory. 1 GB is sufficient for the wellness knowledge base. Prevents unbounded memory growth. |

##### `spring-backend` service

| Field | Before | After | Why |
|---|---|---|---|
| `build` format | `build: ./spring-backend` (short form) | Expanded to `build: context: ./spring-backend` (long form) | Same readability rationale as `python-ai-service`. |
| `networks` | Missing | `networks: - wellness-net` added | Same rationale as all other services. |
| `depends_on.python-ai-service.condition` | `condition: service_started` | `condition: service_healthy` | **This is the most important change.** `service_started` means "the container process has launched". `service_healthy` means "the container has passed its health check". Spring Boot will call `http://python-ai-service:8000` shortly after it starts. With `service_started`, it was possible for Spring Boot to start making AI service calls while FastAPI was still loading ChromaDB and setting up its routes — causing 502 errors on startup. With `service_healthy`, Spring Boot waits until FastAPI confirms it is ready. |
| `healthcheck` | **Entirely missing** | Added full `healthcheck` using `curl --fail --silent http://localhost:8080/actuator/health` | Required for `spring-backend` itself to become `healthy`, which would be needed by any future service that depends on it (e.g., a test runner or integration container). Also provides instant visibility via `docker compose ps` — you can see at a glance whether Spring Boot is up. |
| `restart` | Missing | `restart: unless-stopped` | Auto-recovery if Spring Boot crashes. |
| `deploy.resources.limits.memory` | Missing | `memory: 512m` | Prevents the JVM from growing beyond 512 MB. The JVM flags `-XX:MaxRAMPercentage=75.0` then set the max heap to ~384 MB, leaving ~128 MB for the OS, threads, and off-heap data. |

##### `adminer` service

| Field | Before | After | Why |
|---|---|---|---|
| `networks` | Missing | `networks: - wellness-net` added | Without this, Adminer cannot reach MySQL using the service name `mysql`. |
| `depends_on` | `- mysql` (simple, no health condition) | `mysql: condition: service_healthy` | Adminer is useless if MySQL is not ready. Previously, Adminer could start before MySQL finished initialising, showing a connection error on first open. Now it waits for MySQL to pass its health check before starting. |
| `restart` | Missing | `restart: unless-stopped` | Keeps Adminer running if it crashes. |

##### Top-level `networks` block

| Before | After | Why |
|---|---|---|
| Missing entirely — Docker Compose auto-created a network with an auto-generated name | Added `networks: wellness-net: driver: bridge` | Creating the network explicitly: (1) gives it a predictable, readable name, (2) lets external tools attach to it with `docker network connect wellness-net <container>`, (3) makes the topology visible in documentation and `docker network ls`. |

---

### `.env.example`

#### Original File (before changes)

```
MYSQL_DATABASE=wellness_app
MYSQL_USER=wellness_user
MYSQL_PASSWORD=change_me
MYSQL_ROOT_PASSWORD=change_me_root
MYSQL_HOST_PORT=3307
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/wellness_app
SPRING_DATASOURCE_USERNAME=wellness_user
SPRING_DATASOURCE_PASSWORD=change_me
SPRING_HOST_PORT=8080
JWT_SECRET=replace_with_a_long_random_secret_at_least_32_chars
JWT_EXPIRY_SECONDS=86400
AI_SERVICE_URL=http://python-ai-service:8000
AI_SERVICE_HOST_PORT=8000
INTERNAL_SERVICE_TOKEN=replace_with_internal_token
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_HOST_PORT=11434
OLLAMA_GENERATION_MODEL=qwen2.5:1.5b
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
CHROMA_PERSIST_DIR=/data/chroma
KNOWLEDGE_BASE_DIR=/app/rag-knowledge-base
ADMINER_HOST_PORT=8081
```

#### What Changed

1. **Removed `KNOWLEDGE_BASE_DIR`** — This variable is hard-coded in `docker-compose.yml` as `KNOWLEDGE_BASE_DIR: /app/rag-knowledge-base` because the path is always the same inside the container. Exposing it in `.env.example` implied it was user-configurable, which is misleading.

2. **Added a file header block** — Added a multi-line comment at the top explaining:
   - What this file is
   - That `.env` is excluded from git
   - The exact commands needed to use it (`cp .env.example .env` then `docker compose up -d`)

3. **Added section comments for every variable group** — Each group of related variables now has a heading comment explaining what that section controls and, for variables that are not obvious, an inline explanation of what the value means and how to generate a secure one.

   Specific additions:
   - `MYSQL_HOST_PORT` now explains that 3307 avoids conflicts with a local MySQL on 3306, and that container-to-container traffic still uses `mysql:3306`.
   - `SPRING_DATASOURCE_URL` now explains that `mysql` in the URL is the Docker Compose service name resolved by Docker's internal DNS.
   - `JWT_SECRET` now includes the `openssl rand -hex 32` command to generate a proper secret.
   - `INTERNAL_SERVICE_TOKEN` now includes `openssl rand -hex 24`.
   - `CHROMA_PERSIST_DIR` now explains this path is inside the container and is backed by the `chroma-data` Docker volume.

4. **Variable order** — Variables are now grouped logically by their service (MySQL, Spring Boot, JWT, Python AI, Ollama, ChromaDB, Adminer) instead of being a flat list. This makes it easier to find and change a specific setting.

---

## Files Created (Brand New)

---

### `spring-backend/.dockerignore`

**Why this file is needed:**

When you run `docker build` or `docker compose up --build`, Docker packages the entire build context directory and sends it as a compressed archive to the Docker daemon before reading the Dockerfile. Without `.dockerignore`, Docker would send:

- `target/` — the compiled Maven output directory (can be hundreds of MB of `.class` files and a large `.jar`)
- `.idea/` and `*.iml` — IntelliJ project files (tens of MB)
- Any `.env` file that exists in the directory (real secrets)

This makes builds slower (large upload to daemon) and risks leaking secrets into the image build cache.

**Complete file contents:**

```
target/
.git/
.github/
.idea/
*.iml
.vscode/
.DS_Store
*.md
docs/
.env
.env.*
```

**Explanation of each entry:**

| Pattern | What it excludes | Why |
|---|---|---|
| `target/` | Maven build output (`*.class`, `*.jar`, etc.) | The Dockerfile runs `mvn package` inside the container to produce the JAR. The local `target/` is never needed. Excluding it can save 100–500 MB from the build context. |
| `.git/` | Git object database and history | Git history is not needed to run the application. Sending it inflates the build context and is slow. |
| `.github/` | GitHub Actions workflow files | CI configuration is not needed inside the image. |
| `.idea/` and `*.iml` | IntelliJ IDEA project files | Developer tool files. Not needed to run the app. |
| `.vscode/` | VS Code workspace settings | Developer tool files. Not needed to run the app. |
| `.DS_Store` | macOS finder metadata files | macOS-specific. Not needed anywhere. |
| `*.md` and `docs/` | Markdown documentation files | Documentation is not needed to run the application. |
| `.env` and `.env.*` | Local environment files with real secrets | **Critical.** A local `.env` file may contain real passwords and JWT secrets. If it were copied into the build context, it could end up in an image layer and be visible to anyone who pulls the image. |

---

### `python-ai-service/.dockerignore`

**Why this file is needed:**

The Python build context has the same problem as the Java one, plus Python-specific issues:

- `__pycache__/` directories can contain `.pyc` files that were compiled on your Mac. These are not portable to the container's Linux environment and would be immediately regenerated.
- `.venv/` or `venv/` is the local Python virtual environment. It can be hundreds of MB and is entirely wrong — it contains packages compiled for your Mac's architecture. The container installs the correct packages for Linux via `pip install -r requirements.txt`.
- `chroma-data/` is your local ChromaDB index, which can grow to hundreds of MB. The container uses a Docker volume for this.

**Complete file contents:**

```
__pycache__/
*.pyc
*.pyo
*.pyd
.Python
.pytest_cache/
.coverage
htmlcov/
.venv/
venv/
env/
chroma-data/
.git/
.github/
.vscode/
.DS_Store
*.md
.env
.env.*
```

**Explanation of each entry:**

| Pattern | What it excludes | Why |
|---|---|---|
| `__pycache__/` | Python bytecode cache directories | Mac-compiled `.pyc` files are not portable to Linux. The container regenerates them. |
| `*.pyc`, `*.pyo`, `*.pyd` | Compiled Python files | Same reason as `__pycache__/`. |
| `.Python` | Virtualenv symlink | Created by some virtualenv tools. Not needed. |
| `.pytest_cache/` | Pytest cache | Test tooling cache. Not needed at runtime. |
| `.coverage`, `htmlcov/` | Test coverage reports | Not needed at runtime. |
| `.venv/`, `venv/`, `env/` | Python virtual environments | **Critical.** These contain packages compiled for macOS. Sending them to the Linux container would cause import errors. The Dockerfile installs the correct packages via `pip install`. |
| `chroma-data/` | Local ChromaDB vector index | The container uses a Docker named volume (`chroma-data`) mounted at `/data/chroma`. The local development copy should never be sent to the Docker build. |
| `.git/`, `.github/` | Git metadata | Not needed inside the image. |
| `.vscode/`, `.DS_Store` | IDE and OS files | Not needed inside the image. |
| `*.md` | Markdown files | Documentation is not needed at runtime. |
| `.env`, `.env.*` | Environment files with secrets | Same critical security reason as in the Java `.dockerignore`. |

---

### `RUN_PROJECT_ON_DOCKER.md`

This is a new file created from scratch. It is a 9-step, beginner-friendly guide covering:

| Section | Content |
|---|---|
| Introduction | What Docker is, what containers are, why Docker is useful, what runs in Docker for this project vs what does not |
| Project Architecture | A Mermaid diagram showing all 6 components (Android, Spring Boot, Python AI, Ollama, ChromaDB, MySQL, Adminer) and their network connections |
| Prerequisites | Docker Desktop, key concepts (Image, Container, Network, Volume, docker-compose.yml) — each explained from scratch |
| Step 1 — Prepare Your Environment | `cp .env.example .env`, how to generate real secrets with `openssl rand`, explanation of every variable |
| Step 2 — Start MySQL and Ollama | `docker compose up -d mysql ollama` fully broken down, how MySQL auto-creates the database on first boot, how to connect to MySQL from the host |
| Step 3 — Pull AI Models | `docker compose exec ollama ollama pull qwen2.5:1.5b` and `nomic-embed-text`, download size warning, verification step |
| Step 4 — Build and Start Everything | `docker compose up --build -d` fully broken down, what happens during Maven and pip builds, expected output |
| Step 5 — Verify Everything | `docker compose ps`, health checks via `curl`, `docker compose logs -f`, `docker compose exec`, Adminer login, Android emulator URL |
| Step 6 — Stop the Application | `docker compose stop` vs `docker compose down` vs `docker compose down --volumes`, what data is lost in each case |
| Step 7 — Restart the Application | `docker compose restart`, restarting a single service, re-creating a container after `.env` change |
| Step 8 — Update After Code Changes | `--build` for Java, Python, re-index command for knowledge base changes |
| Step 9 — Troubleshooting | 8 specific failure scenarios with symptoms, root causes, and fixes |
| DevSecOps Notes | Explanation of non-root users, multi-stage builds, JVM container flags, health checks, secret management, resource limits, image size, `.dockerignore`, logging, and CI/CD |
| Quick Reference Card | All commands in a single copy-paste block |

---

## Summary Table

| File | Status | Changes |
|---|---|---|
| `spring-backend/Dockerfile` | Modified | Added: `curl` install, non-root `appuser`, `--chown` on COPY, `USER appuser`, `HEALTHCHECK`, `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` JVM flags, stage named `AS runtime` |
| `python-ai-service/Dockerfile` | Modified | Added: non-root `appuser`, `--chown` on COPY, `USER appuser`, `HEALTHCHECK`, stage named `AS runtime` |
| `docker-compose.yml` | Modified | Added to ALL services: `networks: - wellness-net`, `restart: unless-stopped`, `deploy.resources.limits.memory`. Added to `mysql`: `start_period: 30s`, `--silent` flag. Added to `python-ai-service`: full `healthcheck` block. Changed `spring-backend depends_on.python-ai-service.condition` from `service_started` to `service_healthy`. Added `healthcheck` to `spring-backend` and `adminer`. Added top-level `networks: wellness-net: driver: bridge`. Changed `adminer.depends_on` from simple list to `condition: service_healthy`. |
| `.env.example` | Modified | Removed `KNOWLEDGE_BASE_DIR`. Added file header block. Added section comments for every variable group. Added `openssl` commands for generating secrets. Regrouped variables by service. |
| `spring-backend/.dockerignore` | **Created** | New file excluding `target/`, `.git/`, `.github/`, `.idea/`, `*.iml`, `.vscode/`, `.DS_Store`, `*.md`, `docs/`, `.env`, `.env.*` |
| `python-ai-service/.dockerignore` | **Created** | New file excluding `__pycache__/`, `*.pyc/pyo/pyd`, `.Python`, `.pytest_cache/`, `.coverage`, `htmlcov/`, `.venv/`, `venv/`, `env/`, `chroma-data/`, `.git/`, `.github/`, `.vscode/`, `.DS_Store`, `*.md`, `.env`, `.env.*` |
| `RUN_PROJECT_ON_DOCKER.md` | **Created** | Full beginner Docker guide — 9 steps, Mermaid architecture diagram, all commands explained, 8 troubleshooting scenarios, DevSecOps review section, quick reference card |
