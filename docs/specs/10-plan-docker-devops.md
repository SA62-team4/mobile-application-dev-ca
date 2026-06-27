# 08 Docker And DevOps Spec

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-14, REQ-16, NFR-03, NFR-05 |
| Primary audience | Integration owner, backend owner, Python AI owner |
| Upstream specs | `04-plan-system-architecture.md`, `06-plan-api-contracts.md`, `08-plan-rag-ai-design.md` |
| Downstream specs | Docker Compose, GitHub Actions, setup docs |

## Goal

Dockerise as much of the backend/runtime stack as practical while keeping Android development in Android Studio. Use GitHub Actions to keep the team integration branch healthy.

## Docker Scope

Dockerise:

- MySQL
- Spring Boot backend
- Python FastAPI AI service
- Ollama
- Chroma/vector persistence, either embedded in Python process with a persistent volume or separate service if chosen later
- Optional Adminer or phpMyAdmin

Do not Dockerise:

- Android app runtime or emulator

## Planned Compose Services

| Service | Purpose | Notes |
| --- | --- | --- |
| `mysql` | Transactional database | Named volume for persistent data |
| `spring-backend` | REST API and business logic | Depends on MySQL and Python AI service |
| `python-ai-service` | RAG and agentic AI | Depends on Ollama and vector volume |
| `ollama` | Local model runtime | Named volume for models |
| `adminer` | Optional DB inspection | Demo/debug convenience only |

## Planned Volumes

- `mysql-data`
- `ollama-data`
- `chroma-data`

## Environment Variables

Create `.env.example` during implementation with:

```text
MYSQL_DATABASE=wellness_app
MYSQL_USER=wellness_user
MYSQL_PASSWORD=change_me
MYSQL_ROOT_PASSWORD=change_me_root
MYSQL_HOST_PORT=3307
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/wellness_app
SPRING_DATASOURCE_USERNAME=wellness_user
SPRING_DATASOURCE_PASSWORD=change_me
SPRING_HOST_PORT=8080
JWT_SECRET=replace_with_long_random_secret
JWT_EXPIRY_SECONDS=86400
AI_SERVICE_URL=http://python-ai-service:8000
AI_SERVICE_HOST_PORT=8000
INTERNAL_SERVICE_TOKEN=replace_with_internal_token
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_HOST_PORT=11434
OLLAMA_GENERATION_MODEL=llama3.2:3b
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
CHROMA_PERSIST_DIR=/data/chroma
ADMINER_HOST_PORT=8081
```

Host-facing ports must be configurable so local tools such as Homebrew MySQL do not block Docker Compose. The default MySQL host port is `3307`, while container-to-container traffic continues to use `mysql:3306`.

## Local Setup Contract

Future setup commands should be documented as:

```text
docker compose up -d mysql ollama
docker compose exec ollama ollama pull llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
docker compose up --build
```

Android developers should configure the backend base URL based on emulator/device networking:

- Android emulator to host machine: `http://10.0.2.2:<backend-port>/`
- Physical device primary demo path: USB debugging with `adb reverse tcp:<backend-port> tcp:<backend-port>` and Android URL `http://127.0.0.1:<backend-port>/`
- The Android build may override the default URL with `WELLNESS_API_BASE_URL` as a Gradle property or environment variable; local IP addresses must not be committed.

## GitHub Workflow

Recommended branches:

- `main`: protected and demo-ready.
- `develop`: integration branch.
- `feature/<area>-<short-description>`: feature work.

PR requirements:

- Summary of change.
- Tests run.
- Screenshots for Android UI changes.
- API request/response examples for backend changes.
- Reviewer approval before merge.

## GitHub Actions

CI should run on pull requests to `develop` and `main`.

Jobs:

- Android build and unit tests.
- Spring Boot tests.
- Python tests.
- Docker image build smoke check.
- Compose smoke test for MySQL, Spring Boot, and Python AI service.

Do not run heavyweight Ollama generation in CI by default. Use mocks or a lightweight health-check path so CI stays fast and reliable.

## Optional AWS Hybrid Staging

AWS is optional. It is not the required final demo path.

Recommended simplest AWS path:

- One EC2 instance.
- Docker Compose installed.
- Backend services deployed from the monorepo.
- MySQL either as container or RDS if the team has free-tier confidence.
- Ollama may remain local for final demo to avoid AWS GPU/cost issues.

AWS should be used for:

- Team integration practice.
- Backend smoke testing from Android over a shared URL.
- Presentation architecture discussion.

AWS should not be used for:

- Paid cloud LLM inference.
- A fragile final demo dependency.
- Secrets committed to GitHub.

## Acceptance Criteria

- Docker plan covers all backend/runtime services except Android.
- Environment variables are clearly specified.
- GitHub branch and PR workflow is clear.
- CI jobs are defined at behavior level.
- AWS is clearly optional hybrid staging.
