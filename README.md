# AI-Enabled Wellness Mobile App

SA62 Mobile Application Development CA project implementing a Kotlin Android wellness app, Spring Boot backend, MySQL persistence, local RAG chatbot, and Python agentic AI workflow.

## Repository Layout

| Path | Description |
| --- | --- |
| `android-app/` | Kotlin Android client (XML layouts), JWT auth, wellness CRUD, chatbot, recommendations |
| `spring-backend/` | Java Spring Boot REST API (canonical CA backend), MySQL persistence, JWT security |
| `python-ai-service/` | Python agentic AI service — retrieves wellness records, analyses trends, generates and saves recommendations |
| `rag-knowledge-base/` | Curated wellness knowledge base for the local RAG chatbot |
| `dotnet-backend/` | Optional cold-standby .NET backup backend (contract-compatible, `:8082`) |
| `desktop-app/` | Optional .NET (Avalonia) cross-platform desktop client (bonus, `REQ-21`) |
| `docs/specs/` | Spec Kit lifecycle documents — the source of truth |
| `openwiki/` | Repository overview, architecture, workflows, and testing notes |
| `infra/`, `docker-compose*.yml` | Docker services and deployment configuration |

All AI runs locally and free: Ollama for the LLM, a local vector store for RAG. No paid or cloud-only AI is used at runtime.

Start with the specs:

- `docs/specs/00-spec-kit-index.md`
- `docs/specs/01-constitution-principles.md`
- `docs/specs/12-tasks-implementation-backlog.md`
- `docs/specs/14-validate-quality-gates.md`

## Spec-Driven Development

This project follows Spec-Driven Development (SDD), a spec-first workflow for AI-native engineering. The idea is to align on intent, constraints, acceptance criteria, architecture, tasks, and validation before asking AI tools to generate or change implementation.

The approach is based on Microsoft's SDD guidance: [Spec-Driven Development: A Spec-First Approach to AI-Native Engineering](https://developer.microsoft.com/blog/spec-driven-development-ai-native-engineering). In this repo, the specs are treated as the source of truth for both humans and AI agents.

Local Spec Kit lifecycle:

1. Constitution: `docs/specs/01-constitution-principles.md`
2. Specify: `docs/specs/02-specify-project-requirements.md`
3. Clarify: `docs/specs/03-clarify-decisions-and-edge-cases.md`
4. Plan: `docs/specs/04-plan-system-architecture.md` through `docs/specs/11-plan-implementation-roadmap.md`
5. Tasks: `docs/specs/12-tasks-implementation-backlog.md`
6. Analyze: `docs/specs/13-analyze-traceability-matrix.md`
7. Validate: `docs/specs/14-validate-quality-gates.md` and `docs/specs/15-validate-test-and-demo-plan.md`

Before changing endpoints, schemas, UI flows, AI behavior, Docker services, or demo expectations, update the relevant spec first.

## Local Runtime

```bash
cp .env.example .env
docker compose up -d mysql ollama
docker compose exec ollama ollama pull qwen2.5:1.5b
docker compose exec ollama ollama pull nomic-embed-text
docker compose up --build
```

Android remains outside Docker and should point to the Spring Boot backend:

- Emulator default: `http://10.0.2.2:8080/`
- Physical device over USB: run `tools/scripts/android-phone-demo.sh`, which uses `adb reverse` and `http://127.0.0.1:8080/`

Android backend selection:

```bash
# Spring backend, emulator
WELLNESS_API_BASE_URL=http://10.0.2.2:8080/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug

# Spring backend, physical phone
WELLNESS_API_BASE_URL=http://127.0.0.1:8080/ tools/scripts/android-phone-demo.sh

# Optional .NET backup backend, emulator
WELLNESS_API_BASE_URL=http://10.0.2.2:8082/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug

# Optional .NET backup backend, physical phone
WELLNESS_API_BASE_URL=http://127.0.0.1:8082/ tools/scripts/android-phone-demo.sh
```

Optional cold-standby .NET backend rehearsal:

```bash
docker compose -f docker-compose.yml -f docker-compose.dotnet-backup.yml up --build dotnet-backend python-ai-service mysql ollama adminer
BASE_URL=http://localhost:8082 tools/scripts/backend-contract-smoke.sh
```

Spring Boot remains the canonical CA backend. The .NET backend listens on `8082` only for backup rehearsal.

## Desktop Client (optional bonus)

An optional .NET (Avalonia) desktop client in `desktop-app/` consumes the same Spring Boot REST API as Android (auth, wellness CRUD, chatbot, recommendations). It is bonus cross-platform evidence (`REQ-21`) and does not replace Android or the backend.

```bash
# requires the .NET 10 SDK and a running Spring Boot backend on :8080
cd desktop-app
dotnet run --project src/WellnessDesktop
```

The backend base URL defaults to `http://localhost:8080/` and can be overridden in `desktop-app/src/WellnessDesktop/appsettings.json` or the `WELLNESS_API_BASE_URL` environment variable.

Build standalone Windows/macOS executables (self-contained, no .NET install needed on the target):

```bash
cd desktop-app
./build-desktop.sh            # win-x64, osx-arm64, osx-x64 → artifacts/<rid>/
MAKE_APP=1 ./build-desktop.sh # also wrap macOS builds into WellnessDesktop.app
```

See [desktop-app/README.md](desktop-app/README.md) for full desktop client docs (run, configure, test, package).

## Validation

```bash
plantuml -checkonly docs/specs/*.md
cd spring-backend && mvn test
cd python-ai-service && python3 -m compileall app
dotnet test dotnet-backend/tests/Wellness.Backup.Api.Tests/Wellness.Backup.Api.Tests.csproj
dotnet test desktop-app/tests/WellnessDesktop.Tests/WellnessDesktop.Tests.csproj
BASE_URL=http://localhost:8080 tools/scripts/backend-contract-smoke.sh
```

## AI Usage Declaration

AI is used in this project in two distinct ways. Both are declared here for academic transparency.

### 1. AI features shipped in the product

The wellness app includes AI capabilities as part of its functionality. All of it runs **locally and free** — no paid or cloud-only AI services are used at runtime.

- **Local LLM:** [Ollama](https://ollama.com) hosts `qwen2.5:1.5b` (chat) and `nomic-embed-text` (embeddings).
- **RAG chatbot:** answers are grounded in the curated wellness knowledge base (`rag-knowledge-base/`) via a local vector store.
- **Agentic AI:** the Python service (`python-ai-service/`) retrieves a user's wellness records, analyses trends, generates recommendations, and saves them back through the backend.

### 2. AI assistance used during development

AI coding assistants (Codex, Claude Code) were used to help produce specs, code, tests, and documentation under the guardrails defined in [`docs/specs/01-constitution-principles.md`](docs/specs/01-constitution-principles.md):

- Development followed a Spec-Driven Development workflow; specs remained the source of truth and were reviewed and updated by the team.
- AI-generated changes were kept small, reviewable, and validated (tests and quality gates) before being accepted.
- The team understands and can explain all submitted work; AI was an assistant, not an author of record.

The tools and services above are used for their stated purposes only and do not send project data to paid or cloud-only AI providers at runtime.
