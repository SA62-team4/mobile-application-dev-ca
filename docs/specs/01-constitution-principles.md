# 00 Constitution

<!-- @author Tiong Zhong Cheng -->

## Purpose

This constitution defines the project principles, standards, and guardrails that all specs, tasks, AI-generated code, and validation work must follow.

It is the center of the Spec Kit lifecycle:

1. Constitution
2. Specify
3. Clarify
4. Plan
5. Tasks
6. Implement
7. Validate

## Core Principles

### 1. Assignment Requirements Come First

The project exists to satisfy the SA62 Mobile Application Development CA brief. If a feature idea conflicts with the assignment, choose the assignment requirement.

Required evidence:

- Kotlin Android app using XML layouts.
- Java Spring Boot backend.
- MySQL persistence through backend services.
- JWT login/logout security.
- Wellness CRUD.
- Chatbot through backend.
- Local RAG behavior.
- Python agentic AI feature.
- Integrated solution, video demo, and author comments.

### 2. Specs Control Implementation

Implementation must follow `docs/specs/`. If a behavior is missing, ambiguous, or contradicted, update the spec before building.

Rules:

- Every implementation task must reference `REQ-*` or `NFR-*` IDs.
- API, schema, UI, AI, Docker, and demo changes must update the controlling spec.
- Do not use AI-generated code that drifts from the spec just because it compiles.

### 3. Clear Component Boundaries

Component ownership must stay simple:

- Android handles UI, local token storage, and calls to Spring Boot.
- Spring Boot handles auth, authorization, business rules, and MySQL writes.
- Python handles RAG retrieval, Ollama calls, and agentic recommendation logic.
- MySQL stores transactional app data.
- Chroma stores vector embeddings.
- Ollama provides local/free AI.

### 4. Local AI Only

The LLM must be free/local.

Allowed:

- Ollama generation model: `qwen2.5:1.5b` (default; `llama3.2:3b` allowed when quality is prioritised over latency).
- Ollama embedding model: `nomic-embed-text`.
- Curated local wellness knowledge base.

Not allowed:

- Paid LLM APIs.
- Cloud-only AI dependencies.
- Secrets committed to the repository.

### 5. Demo Reliability Beats Feature Count

Optional features must not endanger the required demo.

Preferred:

- Local Docker demo path.
- Prepared seed data.
- Deterministic agent rules before LLM generation.
- Clear fallback/error behavior.

Optional:

- AWS hybrid staging.
- Rendered diagram exports.
- Scheduled agent runs.

### 6. Security And Privacy Are Default Requirements

Baseline rules:

- JWT protects all non-auth endpoints.
- Backend derives user identity from JWT, not client-provided user ids.
- Users cannot access another user's records, chats, or recommendations.
- Passwords are hashed.
- Internal service token protects backend-to-Python internal operations.
- Logs must not expose passwords, JWTs, or personal wellness details unnecessarily.

### 7. Tests Are Evidence

Tests and demo steps prove the spec was implemented.

Required validation:

- Auth and JWT tests.
- Ownership tests for user-owned APIs.
- Wellness CRUD tests.
- RAG retrieval smoke test.
- Agent decision-rule tests.
- Docker smoke checks.
- Manual Android demo flow.

## Change Policy

A change is acceptable only when:

- The affected spec is updated.
- Requirement traceability still holds.
- The team can explain the change in the demo or submission.
- The change does not introduce paid/cloud-only AI.
- The change does not create source scaffolding during the specs-only phase.

## AI Usage Guardrails

AI agents may help write specs, code, tests, and docs later, but must:

- Read the constitution and relevant specs first.
- Preserve the architecture boundaries.
- Keep generated changes reviewable.
- Prefer small, tested changes.
- Report validation evidence before claiming completion.

