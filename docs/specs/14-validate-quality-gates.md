# 14 Validation Gates

## Purpose

This file defines how to verify that implementation output matches the specs. It corresponds to the Spec Kit **Validate** step.

Validation must compare the built system against the constitution, requirements, traceability matrix, and subsystem specs.

## Gate 1: Spec Completeness

Run before implementation starts.

Checklist:

- Constitution exists and has no unresolved project-level contradiction.
- Requirement IDs are defined.
- Clarification log has defaults for open questions.
- Architecture, ERD, API, Android UI, RAG, agent, Docker, and test specs exist.
- Traceability matrix maps every requirement to evidence and verification.
- Implementation tasks reference requirement IDs.

Pass condition:

- Team can start implementation without deciding architecture from scratch.

## Gate 2: PR-Level Validation

Run for every implementation PR later.

Checklist:

- PR lists affected task IDs and requirement IDs.
- PR updates specs if behavior changed.
- Tests match the changed subsystem.
- No paid/cloud LLM dependency is introduced.
- No direct Android-to-MySQL or Android-to-Python path is introduced.
- No secrets are committed.

Pass condition:

- Reviewer can trace the code change back to one or more specs.

## Gate 3: Integration Validation

Run when backend, Android, Python AI, and Docker work are joined.

Checklist:

- Android can register, log in, and log out.
- JWT protects all non-auth APIs.
- Wellness CRUD works from Android to MySQL.
- Chatbot works through Spring Boot and Python RAG service.
- RAG responses include source snippets.
- Python agent retrieves recent records, analyses trends, and saves recommendations.
- Docker Compose starts required backend/runtime services.

Pass condition:

- The main demo flow works end to end on a clean setup.

## Gate 4: Demo Validation

Run before video recording and submission.

Checklist:

- Demo script fits 15 minutes.
- Seed data exists and shows meaningful trends.
- Ollama models are pulled before demo.
- PlantUML diagrams render or exported images are available.
- ERD matches implemented schema.
- API docs match implemented endpoints.
- Author comments are present in classes or key methods.
- Final zip contains one integrated solution and video demo.

Pass condition:

- Team can present the app against the marking criteria without relying on unstable optional features.

## Spec Conformance Report

Before final submission, prepare a short report with:

- Requirement IDs completed.
- Evidence location for each major feature.
- Tests run.
- Known limitations.
- Optional features included or skipped.

