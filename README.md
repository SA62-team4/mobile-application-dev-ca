# AI-Enabled Wellness Mobile App

SA62 Mobile Application Development CA project implementing a Kotlin Android wellness app, Spring Boot backend, MySQL persistence, local RAG chatbot, and Python agentic AI workflow.

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
docker compose exec ollama ollama pull llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
docker compose up --build
```

Android remains outside Docker and should point to the Spring Boot backend:

- Emulator: `http://10.0.2.2:8080`
- Physical device: `http://<host-lan-ip>:8080`

## Validation

```bash
plantuml -checkonly docs/specs/*.md
cd spring-backend && mvn test
cd python-ai-service && python3 -m compileall app
```
