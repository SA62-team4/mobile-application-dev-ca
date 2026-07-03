# OpenWiki Quickstart

This repository is an AI-enabled wellness app monorepo. The canonical production/demo path is:

- Kotlin Android client for users
- Java Spring Boot backend as the required backend
- MySQL for transactional storage
- Python AI service for local RAG and recommendation workflows
- Ollama for local generation and embeddings
- Docker Compose for local backend/runtime services

The project is spec-driven. The source of truth lives in `docs/specs/`, and top-level guidance in `README.md`, `AGENTS.md`, and `CLAUDE.md` tells contributors to update specs before changing behavior.

## Start here

1. [Repository architecture](architecture.md)
2. [Spring backend and API surface](backend.md)
3. [Android client](android.md)
4. [Local AI services](ai.md)
5. [Operations, Docker, CI, and deployment](operations.md)

## What this repo is for

The app supports wellness tracking, authentication, chatbot-style wellness guidance, and generated recommendations. The main product flow is:

1. A user signs up or logs in on Android.
2. Android calls the Spring Boot backend with JWT authentication.
3. Users create and view wellness records.
4. The chatbot uses a local curated knowledge base plus Ollama-backed generation.
5. The recommendation workflow analyses recent wellness records, chooses a focus, and saves a personalized recommendation.

## Canonical boundaries

- Android talks to Spring Boot, not directly to MySQL or the Python AI service.
- Spring Boot owns auth, authorization, business rules, and MySQL writes.
- Python owns RAG retrieval, Ollama calls, and the agentic recommendation workflow.
- AI remains local/free only; the repo is built around Ollama and a curated knowledge base.
- The `.NET Backup API` is optional cold-standby evidence, not a replacement for Spring Boot.

## Key source references

- Project overview: `README.md`
- Spec index: `docs/specs/00-spec-kit-index.md`
- Architecture: `docs/specs/04-plan-system-architecture.md`
- Data model: `docs/specs/05-plan-backend-data-model-erd.md`
- API contracts: `docs/specs/06-plan-api-contracts.md`
- Android UX: `docs/specs/07-plan-android-ui-flows.md`
- RAG design: `docs/specs/08-plan-rag-ai-design.md`
- Agentic workflow: `docs/specs/09-plan-agentic-ai-workflow.md`
- Docker/DevOps: `docs/specs/10-plan-docker-devops.md`
- Google SSO implementation guide: `docs/google-sso-setup.md`
- SonarQube setup: `docs/sonarqube-community-build.md`

## Working notes for future agents

- Read the specs before changing code; they are the contract.
- Update the controlling spec first when behavior or interfaces change.
- Keep Android, backend, AI, and deployment changes traceable to requirement IDs and validation gates.
- If you are changing auth or SSO behavior, check the Google SSO guide and the backend security config together.
- If you are changing local startup or deployment, check the Docker and DevOps spec plus the operation docs before editing scripts or workflows.

## OpenWiki map

- [Architecture](architecture.md)
- [Backend and API](backend.md)
- [Android client](android.md)
- [AI services](ai.md)
- [Operations](operations.md)
