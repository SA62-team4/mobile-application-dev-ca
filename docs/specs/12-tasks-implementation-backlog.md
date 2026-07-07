# 13 Implementation Tasks

<!-- @author Abu Bakar Nasir, Tiong Zhong Cheng -->

## Purpose

This file translates the spec plan into implementation-ready task units. It corresponds to the Spec Kit **Tasks** step.

Do not execute these tasks until the implementation phase is explicitly requested.

## Task Status Values

- Not started
- In progress
- Blocked
- In review
- Done

## Phase 0: Spec Review

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-000 | All | Review constitution and spec index with team | Whole team | None | Team accepts docs as implementation contract |
| T-001 | All | Confirm open questions in clarification log | Whole team | T-000 | Open questions have defaults or answers |
| T-002 | All | Confirm final team ownership | Whole team | T-001 | Owners are assigned and recorded |

## Phase 1: Scaffold Later

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-101 | REQ-08, REQ-14 | Create monorepo implementation structure | Member 7 | Phase 0 | Planned folders exist and builds can start |
| T-102 | REQ-16 | Add GitHub branch rules and PR template | Member 7 | T-101 | PRs require spec/test evidence |
| T-103 | REQ-16 | Add initial GitHub Actions workflows | Member 7 | T-101 | CI runs skeleton checks |

## Phase 2: Backend Foundation

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-201 | REQ-08, REQ-09 | Scaffold Spring Boot backend | Member 4 | T-101 | Backend health endpoint runs |
| T-202 | REQ-09, REQ-15 | Implement MySQL schema/entities from ERD | Member 5 | T-201 | Schema matches `05-plan-backend-data-model-erd.md` |
| T-203 | REQ-02, REQ-03 | Implement register, login, JWT security | Member 4 | T-201 | Auth tests pass |
| T-204 | NFR-01 | Add ownership security checks | Member 4 | T-203 | Cross-user access tests pass |

## Phase 3: Wellness CRUD

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-301 | REQ-04, REQ-05, REQ-06, REQ-07 | Implement wellness CRUD APIs | Member 5 | T-202, T-203 | API tests pass |
| T-302 | REQ-01, REQ-02 | Implement Android login/register/logout (realigned to AppCompatActivity + View Binding, T-701) | Member 1 | T-203 | Auth works from app |
| T-303 | REQ-04 through REQ-07 | Implement Android wellness screens (realigned to AppCompatActivity + View Binding + ListView/ArrayAdapter, T-701)| Member 2 | T-301, T-302 | CRUD works end to end |

## Phase 4: RAG Chatbot

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-401 | REQ-11, REQ-12 | Scaffold Python RAG service | Member 6 | T-101 | AI health endpoint runs |
| T-402 | REQ-11 | Create curated wellness KB | Member 6 | T-401 | KB files load and chunk |
| T-403 | REQ-11, REQ-12 | Implement Chroma indexing and Ollama embedding | Member 6 | T-402 | RAG indexing test passes |
| T-404 | REQ-10, REQ-11 | Implement backend chat orchestration | Member 5 | T-301, T-403 | Chat API stores answer and sources |
| T-405 | REQ-10 | Implement Android chatbot screen (realigned to AppCompatActivity + View Binding + ListView/ArrayAdapter, T-701)| Member 3 | T-404 | Chat works from app |

## Phase 5: Agentic AI

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-501 | REQ-13 | Implement backend internal record/recommendation APIs | Member 5 | T-301 | Python can retrieve/save through backend |
| T-502 | REQ-13 | Implement agent deterministic trend rules | Member 7 | T-501 | Agent rule tests pass |
| T-503 | REQ-13 | Implement RAG-assisted recommendation generation | Member 7 | T-403, T-502 | Recommendation saved through backend |
| T-504 | REQ-13 | Implement Android recommendations screen (realigned to AppCompatActivity + View Binding + ListView/ArrayAdapter, T-701)| Member 3 | T-503 | Recommendation visible in app |

## Phase 6: Docker, CI, And Demo

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-601 | REQ-14 | Create Docker Compose stack | Member 7 | T-201, T-401 | Backend, MySQL, Python, Ollama start |
| T-602 | REQ-16 | Complete GitHub Actions workflows | Member 7 | T-601 | CI passes on PR, and guarded SonarQube Community Build scans publish dashboard evidence for Spring, Android, Python, and the .NET backup backend when `SONAR_HOST_URL` and `SONAR_TOKEN` are configured |
| T-603 | REQ-17, REQ-20 | Prepare repeatable demo seed data and script | Member 7 | T-303, T-405, T-504 | Demo data can be reset/reseeded and rehearsal fits 15 minutes |
| T-604 | REQ-18, REQ-19 | Final submission checklist | Whole team | T-603 | Zip/video/docs ready |
| T-605 | REQ-16, NFR-01, NFR-02, NFR-03 | Add `SECURITY.md` Codex Security scan workflow and collect scan evidence before merge/submission; use SonarQube Community Build as supplementary quality-dashboard evidence | Member 7 | T-602 | PR and final-submission docs state scan type, scope, findings, fixes, accepted suppressions, and SonarQube quality-gate evidence where configured |
| T-606 | REQ-16 | Author Terraform infra (`infra/terraform/`) for the DigitalOcean Droplet, reserved IP, firewall, DNS, and remote state; cloud-init only bootstraps the `deploy` user + Python for Ansible | Member 7 | T-601 | `terraform apply` provisions the Droplet with the `deploy` user reachable by Ansible |
| T-607 | REQ-16 | Add prod overlay (`docker-compose.prod.yml`) and `Caddyfile` for HTTPS + internal-only services | Member 7 | T-601 | Prod overlay validates and exposes only Caddy 80/443 |
| T-608 | REQ-16 | Author Ansible config + deploy playbooks (`infra/ansible/`, bootstrap + app roles), invoke them from `deploy.yml`, add the `ansible` syntax/lint CI job, and configure the `production` Environment and GitHub secrets | Member 7 | T-606, T-607 | Push to `main` runs the playbook; the play is idempotent and the HTTPS health check passes |

## Optional Phase 7: .NET Backup Backend

These tasks are optional backup evidence and do not replace `REQ-08`, which is still satisfied by the Java Spring Boot backend.

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-701 | REQ-08, REQ-09, REQ-14, NFR-03 | Document `.NET Backup API` cold-standby design in architecture, API, Docker, traceability, and validation specs | Member 7 | T-201, T-601 | Specs state Spring is canonical and .NET is optional backup |
| T-702 | REQ-09, REQ-14, NFR-01 | Scaffold `dotnet-backend/` with status endpoints, config, MySQL schema compatibility, JWT helpers, and author comments | Member 7 | T-701 | `.NET` project builds and health endpoint contract is available |
| T-703 | REQ-02 through REQ-07, REQ-10, REQ-13, NFR-01, NFR-02 | Mirror Spring public and internal API routes in the `.NET Backup API` | Member 7 | T-702, T-301, T-404, T-501 | Backup routes match Spring request/response contracts |
| T-704 | REQ-14, REQ-16, NFR-03, NFR-05 | Add backup Compose override, CI checks, SonarQube scan evidence, and contract smoke checks parameterized by `BASE_URL` | Member 7 | T-702 | Spring path remains unchanged, `.NET` tests run in CI, guarded SonarQube scan publishes the `sa62-wellness-dotnet-backend` project when configured, and backup can be rehearsed on port `8082` |

## Optional Phase 8: .NET Desktop Client

These tasks are optional bonus evidence (`REQ-21`). The desktop client is an additional REST client only; it does not replace Android or any mandatory requirement, and it consumes the same Spring Boot contracts from `06-plan-api-contracts.md`.

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-801 | REQ-21 | Scaffold `desktop-app/` Avalonia solution, `ApiClient`, `SessionStore`, DTOs, config (`BackendBaseUrl` / `WELLNESS_API_BASE_URL`), and packaged build script support for stamping `BACKEND_BASE_URL` into distributable `appsettings.json` files | Bonus owner | T-203 | Solution builds, `dotnet build` passes, and packaged builds can target the DigitalOcean backend without a launch-time environment variable |
| T-802 | REQ-21, REQ-02, REQ-03, NFR-01, NFR-02 | Implement login/register/logout screens with in-memory JWT storage | Bonus owner | T-801, T-203 | Auth works from desktop against Spring |
| T-803 | REQ-21, REQ-04 through REQ-07, NFR-02, NFR-04 | Implement wellness CRUD screens with loading/empty/success/error states | Bonus owner | T-802, T-301 | CRUD works end to end from desktop |
| T-804 | REQ-21, REQ-10 | Implement chatbot screen with answer and source snippets | Bonus owner | T-803, T-404 | Chat works from desktop |
| T-805 | REQ-21, REQ-13 | Implement recommendations screen (generate and list) | Bonus owner | T-803, T-503 | Recommendation visible in desktop |
| T-806 | REQ-21, REQ-16, NFR-05 | Add `dotnet build`/`dotnet test` CI job and DTO/ApiClient unit tests | Bonus owner | T-801 | Desktop job passes in CI without LLM dependency |

## Implementation Rule

Every future implementation PR should list:

- Task IDs.
- Requirement IDs.
- Spec files changed.
- Tests or demo checks run.
