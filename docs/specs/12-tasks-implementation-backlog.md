# 13 Implementation Tasks

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
| T-302 | REQ-01, REQ-02 | Implement Android login/register/logout | Member 1 | T-203 | Auth works from app |
| T-303 | REQ-04 through REQ-07 | Implement Android wellness screens | Member 2 | T-301, T-302 | CRUD works end to end |

## Phase 4: RAG Chatbot

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-401 | REQ-11, REQ-12 | Scaffold Python RAG service | Member 6 | T-101 | AI health endpoint runs |
| T-402 | REQ-11 | Create curated wellness KB | Member 6 | T-401 | KB files load and chunk |
| T-403 | REQ-11, REQ-12 | Implement Chroma indexing and Ollama embedding | Member 6 | T-402 | RAG indexing test passes |
| T-404 | REQ-10, REQ-11 | Implement backend chat orchestration | Member 5 | T-301, T-403 | Chat API stores answer and sources |
| T-405 | REQ-10 | Implement Android chatbot screen | Member 3 | T-404 | Chat works from app |

## Phase 5: Agentic AI

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-501 | REQ-13 | Implement backend internal record/recommendation APIs | Member 5 | T-301 | Python can retrieve/save through backend |
| T-502 | REQ-13 | Implement agent deterministic trend rules | Member 7 | T-501 | Agent rule tests pass |
| T-503 | REQ-13 | Implement RAG-assisted recommendation generation | Member 7 | T-403, T-502 | Recommendation saved through backend |
| T-504 | REQ-13 | Implement Android recommendations screen | Member 3 | T-503 | Recommendation visible in app |

## Phase 6: Docker, CI, And Demo

| Task ID | Requirement IDs | Task | Owner | Depends On | Done When |
| --- | --- | --- | --- | --- | --- |
| T-601 | REQ-14 | Create Docker Compose stack | Member 7 | T-201, T-401 | Backend, MySQL, Python, Ollama start |
| T-602 | REQ-16 | Complete GitHub Actions workflows | Member 7 | T-601 | CI passes on PR |
| T-603 | REQ-17, REQ-20 | Prepare repeatable demo seed data and script | Member 7 | T-303, T-405, T-504 | Demo data can be reset/reseeded and rehearsal fits 15 minutes |
| T-604 | REQ-18, REQ-19 | Final submission checklist | Whole team | T-603 | Zip/video/docs ready |

## Implementation Rule

Every future implementation PR should list:

- Task IDs.
- Requirement IDs.
- Spec files changed.
- Tests or demo checks run.
