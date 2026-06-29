# Agent Guidance

## Project Intent

This repository follows spec-driven development for the SA62 Mobile Application Development Continuous Assessment project. The target system is an AI-enabled wellness mobile app with:

- Kotlin Android app using XML layouts
- Java Spring Boot backend
- MySQL persistence
- JWT authentication
- Basic local RAG chatbot
- Python-based agentic AI recommendation workflow
- Dockerised backend/runtime services where practical

Implementation has started. Work from the specs under `docs/specs/` first, and update the relevant spec before changing behavior, contracts, schemas, UI flows, AI logic, Docker services, or demo expectations.

## Source Of Truth

Read these files before making implementation decisions:

1. `docs/specs/00-spec-kit-index.md`
2. `docs/specs/01-constitution-principles.md`
3. `docs/specs/02-specify-project-requirements.md`
4. `docs/specs/03-clarify-decisions-and-edge-cases.md`
5. `docs/specs/04-plan-system-architecture.md`
6. `docs/specs/05-plan-backend-data-model-erd.md`
7. `docs/specs/06-plan-api-contracts.md`
8. `docs/specs/11-plan-implementation-roadmap.md`
9. `docs/specs/12-tasks-implementation-backlog.md`
10. `docs/specs/13-analyze-traceability-matrix.md`
11. `docs/specs/14-validate-quality-gates.md`
12. The component-specific spec for the task

If a future implementation request conflicts with the CA brief, preserve the CA requirement and ask for clarification.

## Spec-Driven Development Workflow

- Treat `docs/specs/` as the project contract.
- Follow the GitHub Spec Kit lifecycle: Constitution, Specify, Clarify, Plan, Tasks, Implement, Validate.
- Use `13-analyze-traceability-matrix.md` and `14-validate-quality-gates.md` as the Analyze step before implementation.
- Before implementation, identify the affected requirement IDs from `13-analyze-traceability-matrix.md`.
- During implementation, identify affected task IDs from `12-tasks-implementation-backlog.md`.
- Update the relevant spec before changing behavior, endpoints, schemas, UI flows, AI logic, Docker services, or demo expectations.
- Prefer extending an existing spec over creating a new one. Fold a new feature into the spec that already owns its lifecycle phase (for example, a new Android screen goes in `07-plan-android-ui-flows.md`, a new endpoint in `06-plan-api-contracts.md`). Only create a new spec file for a genuinely new lifecycle artifact that no existing spec covers.
- Name spec files with the Spec Kit lifecycle convention `NN-<phase>-<topic>.md`, where `<phase>` is one of `constitution`, `specify`, `clarify`, `plan`, `tasks`, `analyze`, or `validate`. Do not add spec files without a lifecycle-phase prefix.
- Keep PRs traceable by listing requirement IDs, spec files changed, and verification evidence.
- Do not introduce implementation behavior that is not described in the specs.
- Keep implementation changes traceable to requirement IDs and task IDs.

## Non-Negotiable Constraints

- Android must call the Spring Boot backend, not MySQL or the Python AI service directly.
- Spring Boot owns authentication, authorization, business rules, and MySQL writes.
- Python owns RAG retrieval, Ollama calls, and agentic recommendation analysis.
- AI must be free/local. Do not introduce paid LLM APIs or cloud-only model dependencies.
- Use Ollama as the default local LLM runtime.
- Use `llama3.2:3b` for generation and `nomic-embed-text` for embeddings unless the specs are revised.
- Dockerise MySQL, Spring Boot, Python AI service, Ollama, and vector persistence where practical.
- Android remains outside Docker.
- Add author comments to classes or key methods during implementation, as required by the assignment.
- Do not commit real secrets, tokens, database passwords, JWT signing keys, or API keys.

## Planned Repository Layout

The implementation phase should use this monorepo layout:

```text
android-app/
spring-backend/
python-ai-service/
rag-knowledge-base/
docs/
  specs/
.github/
  workflows/
docker-compose.yml
.env.example
```

This layout now exists or is planned as the implementation structure.

## Collaboration Rules

- Keep changes small enough for review.
- Use feature branches from `develop`.
- Keep `main` protected and demo-ready.
- Every PR should include a summary, test evidence, and screenshots or API examples when relevant.
- Update specs when changing endpoints, database fields, architecture, AI behavior, or demo flow.
- Prefer testable behavior over clever abstractions.
- Keep the final demo reliable even if optional AWS staging is unavailable.

## Recommended Checks

```text
cd spring-backend && ./mvnw test
cd python-ai-service && pytest
cd android-app && ./gradlew test
docker compose up --build
```

Run the relevant checks for the component being changed and report the evidence in the final response or PR notes.
