# Architecture

The repository is organized around a canonical Spring Boot backend with supporting mobile, AI, and operational services.

## System shape

Primary runtime boundaries:

- **Android app**: Kotlin/XML client for login, records, chat, recommendations, and profile flows.
- **Spring Boot backend**: canonical API, JWT auth, business rules, and MySQL persistence.
- **MySQL**: transactional storage for users, wellness records, chat messages, and recommendations.
- **Python AI service**: local RAG and agentic recommendation orchestration.
- **Ollama**: local runtime for generation and embeddings.
- **Optional .NET Backup API**: cold-standby REST mirror used for rehearsal evidence, not the main product path.
- **Optional desktop client**: Avalonia-based parity client that uses the same REST backend as Android.

The high-level architecture is defined in `docs/specs/04-plan-system-architecture.md` and reinforced by `docs/specs/10-plan-docker-devops.md`.

## Why this shape exists

The architecture is constrained by the assignment and project specs:

- Android must remain a client, not a data or AI backend.
- Spring Boot is the required backend and owns auth, authorization, and persistence.
- Python isolates the local AI workload so chatbot and recommendation logic can evolve without moving business rules out of the backend.
- Ollama keeps AI local and free, which is a hard project constraint.
- The optional .NET backend exists for backup/cold-standby evidence and should not drift from the Spring contracts.

## Runtime topology

Local development uses Docker Compose for backend/runtime services and Android Studio for the mobile client. The default demo path keeps Spring Boot on port `8080`; backup rehearsal can expose the .NET backend on `8082`.

Operational entry points and environment conventions are documented in:

- `README.md`
- `RUN_PROJECT_ON_DOCKER.md`
- `docker-compose.yml`
- `docker-compose.dotnet-backup.yml`
- `docs/specs/10-plan-docker-devops.md`

## Boundaries future agents should preserve

When changing code, watch for these invariants:

- Do not let Android bypass Spring Boot for MySQL or Python.
- Keep the backend contract aligned across Spring and any backup implementation.
- Keep local AI free and Ollama-based.
- Keep deployment changes consistent with the docs/specs before editing workflows or compose files.
- Treat the backup backend and desktop client as optional; do not let them redefine the main architecture.

## Best starting points

- `docs/specs/04-plan-system-architecture.md`
- `docs/specs/10-plan-docker-devops.md`
- `README.md`
- `docker-compose.yml`
- `.github/workflows/`
