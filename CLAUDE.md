# Claude Guidance

Read `AGENTS.md` first. Then read `docs/specs/00-spec-kit-index.md` and `docs/specs/01-constitution-principles.md`. Together they define the source of truth for this project.

## Current Phase

This repository is in active implementation using specs as the contract. Read and update the relevant specs before changing behavior, endpoints, schemas, UI flows, AI behavior, Docker services, or demo expectations.

## Working Style

- Inspect the relevant spec before changing anything.
- Identify affected requirement IDs from `docs/specs/13-analyze-traceability-matrix.md`.
- Identify affected task IDs from `docs/specs/12-tasks-implementation-backlog.md` once implementation begins.
- Use `docs/specs/14-validate-quality-gates.md` before claiming work is complete.
- Use a todo list for multi-step implementation tasks.
- Prefer small, reviewable changes.
- Keep architecture aligned with the CA requirements.
- When changing planned behavior, update the corresponding file in `docs/specs/`.
- Before reporting implementation work complete, run the relevant tests once code exists.

## Important Project Constraints

- Android uses Kotlin and XML layouts.
- Backend uses Java Spring Boot.
- Database is MySQL.
- Authentication uses JWT.
- AI is local/free only, using Ollama.
- RAG uses a curated wellness knowledge base and a local vector store.
- Python agentic AI retrieves user wellness records, analyses trends, generates recommendations, and saves them through the backend.
- Docker is used for backend/runtime services where practical; Android remains outside Docker.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
