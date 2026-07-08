# 10 Implementation Roadmap

<!-- @author Tiong Zhong Cheng -->

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
| 6 | Docker, CI, deployment, and security review | Compose stack, GitHub Actions CI (incl. Ansible lint) + deploy/infra workflows, Terraform infra, Ansible config + deploy, DigitalOcean deployment, Codex Security scan guidance, smoke checks, setup docs stable |
| 7 | Demo hardening | Seed data, video script, final testing, zipped submission checklist complete |
| Optional 8 | .NET backup rehearsal | Cold-standby `.NET Backup API` mirrors Spring contracts on port `8082` without replacing Spring |
| Optional 9 | Privacy stretch | Android Privacy screen plus Spring account export/delete endpoints work end to end without weakening the local demo path |

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
12. Add Docker Compose, health checks, CI, and `SECURITY.md` Codex Security scan guidance.
13. Finalize ERD/API/setup/demo docs, security review evidence, and author comments.
14. Optional: add `.NET Backup API` scaffold, backup Compose override, parity smoke checks, and Codex Security scan evidence after Spring contracts are stable.
15. Optional: implement `REQ-23` privacy screen and account export/delete only after Android Profile and backend ownership tests are stable.

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
| .NET backup backend | Member 7 | Spring API contracts and MySQL schema stable | Optional backup rehearsal only |
| Privacy stretch | Members 4 + 1 | Backend ownership checks and Android Profile stable | Optional private/standout demo evidence |

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
- Codex Security repository or scoped scans have been run for changed runtime components, with findings fixed or documented.
- Demo script rehearsed under 15 minutes.
- Author comments added.
- Final zip contains one integrated solution and video demo.

### Optional Milestone F: Backup Backend Ready

- `.NET Backup API` builds and exposes Spring-compatible health/status endpoints.
- Backup API uses the same MySQL schema, JWT secret, BCrypt-compatible passwords, and JSON/error shapes as Spring.
- Backup Compose override exposes .NET on host port `8082`.
- Contract smoke checks can target either Spring `8080` or .NET `8082` with `BASE_URL`.

### Optional Milestone G: Privacy Stretch Ready

- Profile links to a Privacy screen that explains local AI and data ownership.
- `GET /api/account/export` returns only the authenticated user's export payload.
- `DELETE /api/account` removes the authenticated user's account data transactionally.
- Android export opens a share/save flow; Android delete clears local auth and returns to Login.
- Ownership/security tests cover cross-user export isolation and post-delete token behavior.

## Risk Controls

- Keep local Docker demo as the primary path.
- Treat AWS as optional staging only.
- Use deterministic agent rules before LLM generation to make behavior explainable.
- Mock or bypass heavyweight Ollama generation in CI.
- Prepare demo data before the recording.
- Keep `.NET Backup API` as cold standby so the main Spring Boot demo path remains simple and assignment-compliant.
