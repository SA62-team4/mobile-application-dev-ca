# 10 Implementation Roadmap

## Goal

Define the build order for the future implementation phase so the team can work in parallel without breaking architectural boundaries. This is not source code and does not create scaffold files.

## Phase Gates

| Phase | Gate | Exit Criteria |
| --- | --- | --- |
| 0 | Specs approved | Requirements, ERD, API, Android flow, RAG, agent, Docker, and demo specs reviewed |
| 1 | Repository scaffold | Monorepo folders created, build tools selected, README/setup skeleton added |
| 2 | Backend foundation | Spring Boot auth, JWT, MySQL connection, user model, health checks working |
| 3 | Wellness CRUD | Backend CRUD and Android CRUD screens work end to end |
| 4 | Local RAG | Python service indexes KB, retrieves chunks, calls Ollama, backend stores chat |
| 5 | Agentic AI | Agent analyses records, saves recommendations, Android displays output |
| 6 | Docker and CI | Compose stack, GitHub Actions, smoke checks, setup docs stable |
| 7 | Demo hardening | Seed data, video script, final testing, zipped submission checklist complete |

## Recommended Build Order

1. Create monorepo scaffold and shared documentation.
2. Implement Spring Boot health endpoint, MySQL configuration, and base security.
3. Implement auth/register/login/JWT before Android authenticated screens.
4. Implement wellness record APIs and database persistence.
5. Implement Android login/register and token storage.
6. Implement Android wellness list/create/edit/delete screens.
7. Implement Python RAG service with curated KB and Ollama integration.
8. Connect backend chatbot endpoint to Python RAG service.
9. Implement Android chatbot screen and chat history.
10. Implement Python agentic recommendation workflow.
11. Connect backend recommendation endpoints and Android recommendation screen.
12. Add Docker Compose, health checks, and CI.
13. Finalize ERD/API/setup/demo docs and author comments.

## Parallel Work Streams

| Stream | Owner | Can Start After | Blocks |
| --- | --- | --- | --- |
| Android auth/navigation | Member 1 | Phase 1 scaffold | Auth demo flow |
| Android wellness CRUD | Member 2 | API DTOs agreed | Wellness demo flow |
| Android chat/recommendations | Member 3 | Chat and recommendation API contracts agreed | AI demo flow |
| Backend auth/security | Member 4 | Phase 1 scaffold | All protected APIs |
| Backend domain APIs | Member 5 | ERD and auth user model agreed | Android CRUD, AI saves |
| RAG service | Member 6 | AI service scaffold and KB format agreed | Chatbot |
| Agent/Docker/CI | Member 7 | Backend internal API contract agreed | Recommendation demo, integration |

## Milestone Acceptance

### Milestone A: Backend Contract Ready

- Auth endpoints return documented responses.
- Wellness endpoints match `06-plan-api-contracts.md`.
- MySQL schema matches `05-plan-backend-data-model-erd.md`.
- JWT protects non-auth endpoints.

### Milestone B: Android CRUD Ready

- User can register/login/logout.
- Records screen can create, list, update, and delete records.
- Expired or missing JWT returns user to login.

### Milestone C: RAG Chat Ready

- Curated KB can be indexed.
- Chatbot retrieves sources and generates a local Ollama answer.
- Chat history is saved and shown in Android.

### Milestone D: Agent Ready

- Agent retrieves recent records through backend.
- Agent chooses a focus using deterministic rules.
- Agent saves a recommendation through backend.
- Android displays saved recommendations.

### Milestone E: Submission Ready

- Docker Compose stack works on a clean machine.
- GitHub Actions pass.
- Demo script rehearsed under 15 minutes.
- Author comments added.
- Final zip contains one integrated solution and video demo.

## Risk Controls

- Keep local Docker demo as the primary path.
- Treat AWS as optional staging only.
- Use deterministic agent rules before LLM generation to make behavior explainable.
- Mock or bypass heavyweight Ollama generation in CI.
- Prepare demo data before the recording.

