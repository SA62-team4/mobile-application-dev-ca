# 11 Traceability Matrix

<!-- @author Abu Bakar Nasir, Tiong Zhong Cheng -->

## Purpose

This matrix links each requirement to the specs, implementation evidence, and tests that should prove it. During implementation, every PR should mention the requirement IDs it affects.

## Requirement Traceability

| ID | Requirement | Controlled By | Evidence During Implementation | Verification |
| --- | --- | --- | --- | --- |
| REQ-01 | Android app uses Kotlin with XML layouts | `07-plan-android-ui-flows.md` | `android-app/` screens and layouts, Figma UI spec | Android build, manual UI demo, Figma-to-XML review, confirm AppCompatActivity + View Binding + explicit-Intent navigation + ListView/ArrayAdapter idiom (T-701)|
| REQ-02 | User can register, log in, and log out | `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md` | Auth API, login/register/profile screens, Figma auth/profile frames | Auth tests, demo login/logout |
| REQ-03 | JWT protects non-auth APIs | `06-plan-api-contracts.md`, `05-plan-backend-data-model-erd.md`, `10-plan-docker-devops.md` | Spring Security config with `hasRole(USER)` gate, `Role` enum (`USER`/`PREMIUM_USER`), `role` JWT claim, token storage | Security tests, missing-token checks, role/authority tests |
| REQ-04 | User can create wellness records | `05-plan-backend-data-model-erd.md`, `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md` | Create API, form screen, MySQL row, Figma add/edit frame | Backend test, Android manual QA |
| REQ-05 | User can retrieve current and historical wellness records | `05-plan-backend-data-model-erd.md`, `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md` | List/detail API, records screen, Figma records frame | Backend test, demo record list |
| REQ-06 | User can update wellness records | `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md` | Update API, edit screen, Figma add/edit frame | Backend test, demo edit |
| REQ-07 | User can delete wellness records | `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md` | Delete API, delete confirmation, Figma destructive action pattern | Backend test, demo delete |
| REQ-08 | Backend uses Java Spring Boot | `04-plan-system-architecture.md`, `10-plan-docker-devops.md` | `spring-backend/` project; optional `.NET Backup API` documented only as cold standby | Spring backend build and health check |
| REQ-09 | Data is stored and retrieved from MySQL through backend services | `05-plan-backend-data-model-erd.md`, `06-plan-api-contracts.md` | JPA entities/repositories, MySQL container | Persistence tests, DB inspection |
| REQ-10 | Chatbot accepts questions from Android through backend | `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md`, `08-plan-rag-ai-design.md` | Chat endpoint and Android chat screen, Figma chatbot frame and AI waiting/error states | Integration test, demo question |
| REQ-11 | Chatbot uses basic local RAG | `08-plan-rag-ai-design.md` | KB files, Chroma index, retrieval code | RAG smoke test, source snippets |
| REQ-12 | LLM is free/local only | `08-plan-rag-ai-design.md`, `10-plan-docker-devops.md` | Ollama configuration, no paid API client | Config review, demo Ollama models |
| REQ-13 | Python agentic AI retrieves data, analyses trends, generates and saves recommendations | `09-plan-agentic-ai-workflow.md`, `07-plan-android-ui-flows.md` | Python agent workflow, backend internal APIs, Figma recommendation and local AI waiting frames | Agent tests, demo recommendation |
| REQ-14 | Dockerise backend/runtime services where practical | `10-plan-docker-devops.md` | Compose services and volumes; optional backup Compose override for `.NET Backup API` | Docker smoke test; optional backup health check on port `8082` |
| REQ-15 | ERD documents backend data model | `05-plan-backend-data-model-erd.md` | PlantUML ERD and optional rendered export | Review against implemented schema |
| REQ-16 | GitHub collaboration, CI, and deployment are defined | `10-plan-docker-devops.md`, `11-plan-implementation-roadmap.md`, `SECURITY.md` | Branch rules, PR template, CI workflows (incl. `ansible` syntax/lint job and guarded SonarQube scans for Spring, Android, Python, and `.NET Backup API`), deploy/infra workflows (`deploy.yml` running `ansible-playbook`, `infra.yml` for app and SonarQube infrastructure), `docker-compose.prod.yml`, `docker-compose.sonar.yml`, `Caddyfile`, `Caddyfile.sonar`, `infra/terraform/`, `infra/ansible/` (bootstrap + app + SonarQube roles), Codex Security scan evidence, SonarQube dashboard evidence | Actions pass, PR review, Terraform apply + Ansible deploy from Actions with HTTPS health check, SonarQube quality gate evidence for configured components, `ansible-playbook --syntax-check` + `ansible-lint` green, Codex Security scan summary |
| REQ-17 | Video demo fits assignment expectations | `15-validate-test-and-demo-plan.md` | Demo script and recording | Rehearsed 15-minute flow |
| REQ-18 | Author is indicated in classes or key methods | `02-specify-project-requirements.md`, `15-validate-test-and-demo-plan.md` | Author comments during implementation | Submission checklist review |
| REQ-19 | Single integrated zipped submission | `02-specify-project-requirements.md`, `15-validate-test-and-demo-plan.md` | Final zip named with team name | Submission checklist review |
| REQ-20 | Mock data can be populated into MySQL for demo rehearsal | `02-specify-project-requirements.md`, `15-validate-test-and-demo-plan.md`, `12-tasks-implementation-backlog.md` | Backend seed profile or documented API seed script | Demo data reset/reseed check |
| REQ-21 | Optional .NET (Avalonia) desktop client consumes the Spring Boot REST API (bonus) | `02-specify-project-requirements.md`, `04-plan-system-architecture.md`, `06-plan-api-contracts.md` | `desktop-app/` Avalonia client, shared REST contracts, in-memory JWT | `dotnet build`/`dotnet test`, manual desktop demo of auth→CRUD→chat→recommendation |
| REQ-22 | Optional Google SSO as an additional Android login path (bonus) | `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md`, `05-plan-backend-data-model-erd.md`, `10-plan-docker-devops.md` | `GoogleTokenVerifier`, `POST /api/auth/google`, nullable `password_hash`, Android Google Sign-In button, `GOOGLE_CLIENT_ID` env across Compose/CI/Ansible | Backend token-verify tests (400/401), manual emulator Google sign-in demo, `docs/google-sso-setup.md` / `docs/local-sso-quickstart.md` |
| REQ-23 | Optional privacy screen, account data export, and account deletion (stretch) | `03-clarify-decisions-and-edge-cases.md`, `04-plan-system-architecture.md`, `05-plan-backend-data-model-erd.md`, `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md`, `12-tasks-implementation-backlog.md` | Spring `GET /api/account/export` and `DELETE /api/account`, Android Privacy screen launched from Profile, JSON export share/save flow, destructive delete confirmation | Backend ownership/export/delete tests, post-delete JWT access check, Android manual QA for export/delete/offline/expired-token states |

## Cross-Cutting Non-Functional Requirements

| ID | Requirement | Controlled By | Verification |
| --- | --- | --- | --- |
| NFR-01 | App must prevent one user from accessing another user's data | `05-plan-backend-data-model-erd.md`, `06-plan-api-contracts.md`, `SECURITY.md` | Ownership tests for every user-owned API, Codex Security scan of auth/ownership changes |
| NFR-02 | Errors must be user-friendly and not expose stack traces | `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md`, `SECURITY.md`, `10-plan-docker-devops.md` | API error tests, Android manual QA, Figma error-state review, Codex Security review of error/log handling, SonarQube maintainability/reliability issue review |
| NFR-03 | Demo must work without paid services | `08-plan-rag-ai-design.md`, `10-plan-docker-devops.md`, `SECURITY.md` | Offline/local model demo check, Codex Security review for paid/cloud-only AI or scanner dependencies |
| NFR-04 | Major screens must show loading, empty, success, and error states | `07-plan-android-ui-flows.md` | Android manual QA checklist, Figma state frames |
| NFR-05 | CI should avoid heavyweight local LLM generation | `10-plan-docker-devops.md` | Workflow review and CI run; SonarQube scans run without Ollama generation |

## PR Traceability Template

Future PRs should include:

```text
Requirement IDs:
- REQ-__
- NFR-__

Spec files updated:
- docs/specs/__

Verification:
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual Android check
- [ ] Docker smoke check
- [ ] Demo script still valid
```

Optional `.NET Backup API` PRs should also list task IDs `T-701` through `T-704` as applicable and state that Spring Boot remains the canonical `REQ-08` backend.
