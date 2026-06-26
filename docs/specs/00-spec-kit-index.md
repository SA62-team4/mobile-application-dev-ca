# 00 Spec Kit Index

## Purpose

This directory is the contract for the project. Implementation work should read the relevant specs first, then update the specs when behavior, interfaces, schemas, AI behavior, Docker services, or demo expectations change.

The specs follow the GitHub Spec Kit engineering lifecycle:

1. Constitution: define principles, standards, and guardrails.
2. Specify: capture requirements, scenarios, and acceptance criteria.
3. Clarify: resolve ambiguity, dependencies, and edge cases.
4. Plan: translate intent into architecture, flows, and constraints.
5. Tasks: break the work into implementation-ready units.
6. Implement: use AI to generate and refine code and tests.
7. Validate: verify that output matches the spec.

The workflow image also includes Analyze. In this project, Analyze is treated as the review step between Tasks and Implement, using traceability and validation gates to catch gaps before code is generated.

## Lifecycle Map

| Spec Kit Step | Local File(s) | Outcome |
| --- | --- | --- |
| Constitution | `01-constitution-principles.md` | Project principles, standards, guardrails |
| Specify | `02-specify-project-requirements.md` | Requirement IDs, acceptance criteria, team ownership |
| Clarify | `03-clarify-decisions-and-edge-cases.md` | Resolved decisions, open questions, edge cases |
| Plan | `04-plan-system-architecture.md` through `11-plan-implementation-roadmap.md` | Architecture, data, API, UI, AI, Docker, roadmap |
| Tasks | `12-tasks-implementation-backlog.md` | Implementation-ready task units |
| Analyze | `13-analyze-traceability-matrix.md`, `14-validate-quality-gates.md` | Gap analysis before implementation |
| Implement | Future code and tests | AI-assisted implementation against specs |
| Validate | `15-validate-test-and-demo-plan.md`, `14-validate-quality-gates.md` | Evidence that output matches spec |

## Reading Order

| Order | Spec | Purpose |
| --- | --- | --- |
| 1 | `01-constitution-principles.md` | Principles, standards, and guardrails |
| 2 | `02-specify-project-requirements.md` | Assignment scope, requirement IDs, team ownership, marking fit |
| 3 | `03-clarify-decisions-and-edge-cases.md` | Resolved decisions, open questions, edge cases |
| 4 | `04-plan-system-architecture.md` | Component boundaries, data flow, deployment shape |
| 5 | `05-plan-backend-data-model-erd.md` | Backend MySQL data model and ownership rules |
| 6 | `06-plan-api-contracts.md` | REST contracts, request/response shapes, auth rules |
| 7 | `07-plan-android-ui-flows.md` | XML Android screens, navigation, validation, states |
| 8 | `08-plan-rag-ai-design.md` | Local RAG knowledge base, retrieval, prompting, fallback behavior |
| 9 | `09-plan-agentic-ai-workflow.md` | Python recommendation agent workflow and decision rules |
| 10 | `10-plan-docker-devops.md` | Docker, GitHub Actions, optional AWS staging |
| 11 | `11-plan-implementation-roadmap.md` | Build order, phase gates, parallel work plan |
| 12 | `12-tasks-implementation-backlog.md` | Implementation-ready task units |
| 13 | `13-analyze-traceability-matrix.md` | Requirement-to-spec-to-test mapping |
| 14 | `14-validate-quality-gates.md` | Spec conformance gates |
| 15 | `15-validate-test-and-demo-plan.md` | Verification strategy and 15-minute demo plan |

## Spec Status

Current status: **Implementation active, specs remain the source of truth.**

Status meanings:

- Draft: can be edited freely.
- Review: team should comment before implementation starts.
- Approved: implementation should follow this spec unless a change is approved.
- Superseded: retained for history but no longer controls implementation.

## Change Control

When a behavior changes:

- Update the controlling spec first.
- Update `13-analyze-traceability-matrix.md` if a requirement, endpoint, entity, UI flow, or test changes.
- Update `11-plan-implementation-roadmap.md` if the build order or ownership changes.
- Update `03-clarify-decisions-and-edge-cases.md` when a decision resolves ambiguity.
- Update `12-tasks-implementation-backlog.md` when task boundaries or dependencies change.
- Update `14-validate-quality-gates.md` when acceptance evidence changes.
- Keep PlantUML diagrams aligned with the written architecture.
- Do not silently introduce behavior that is absent from the specs.

## Implementation Entry Criteria

Implementation may begin when:

- Requirement IDs in `02-specify-project-requirements.md` are accepted by the team.
- API and ERD specs are clear enough for backend work.
- Android screen flow is clear enough for UI work.
- RAG and agent specs are clear enough for AI work.
- Docker and CI expectations are clear enough for integration work.
- Task IDs in `12-tasks-implementation-backlog.md` are accepted by the team.
- Validation gates in `14-validate-quality-gates.md` are accepted by the team.

## Implementation Boundary

Implementation is active in this repository. Specs still control the work:

- Update specs before changing behavior or contracts.
- Keep Android, backend, Python AI, Docker, and demo behavior traceable to requirement IDs.
- Use the Tasks, Analyze, and Validate files before claiming implementation work is complete.
