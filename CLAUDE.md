# Claude Guidance

Read `AGENTS.md` first. Then read `openwiki/quickstart.md`, `docs/specs/00-spec-kit-index.md`, and `docs/specs/01-constitution-principles.md`. Together they define the source of truth for this project.

## OpenWiki

This repository has documentation located in the /openwiki directory.

Start here:
- [OpenWiki quickstart](openwiki/quickstart.md)

OpenWiki includes repository overview, architecture notes, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

When working in this repository, read the OpenWiki quickstart first, then follow its links to the relevant architecture, workflow, domain, operation, and testing notes.

## Current Phase

This repository is in active implementation using specs as the contract. Read and update the relevant specs before changing behavior, endpoints, schemas, UI flows, AI behavior, Docker services, or demo expectations.

## Working Style

- Inspect the relevant spec before changing anything.
- Identify affected requirement IDs from `docs/specs/13-analyze-traceability-matrix.md`.
- Identify affected task IDs from `docs/specs/12-tasks-implementation-backlog.md` once implementation begins.
- Use `docs/specs/14-validate-quality-gates.md` before claiming work is complete.
- Use a todo list for multi-step implementation tasks.
- Prefer small, reviewable changes.
- Keep architecture aligned with the CA requirements.
- When changing planned behavior, update the corresponding file in `docs/specs/`.
- Prefer extending an existing spec over creating a new one. Fold a new feature into the spec that already owns its lifecycle phase (for example, a new Android screen goes in `07-plan-android-ui-flows.md`). Only add a new spec file for a genuinely new lifecycle artifact, and name it with the Spec Kit convention `NN-<phase>-<topic>.md` (`<phase>` ∈ constitution, specify, clarify, plan, tasks, analyze, validate).
- Before reporting implementation work complete, run the relevant tests once code exists.

## Important Project Constraints

- Android uses Kotlin and XML layouts.
- Backend uses Java Spring Boot.
- Database is MySQL.
- Authentication uses JWT.
- AI is local/free only, using Ollama.
- RAG uses a curated wellness knowledge base and a local vector store.
- Python agentic AI retrieves user wellness records, analyses trends, generates recommendations, and saves them through the backend.
- Docker is used for backend/runtime services where practical; Android remains outside Docker.
- Automatically add `@author Name` comments to new classes, components, test
  classes, important scripts, and key methods to satisfy `REQ-18`. Prefer an
  explicit author from the user/task/nearby convention; otherwise use
  `git config user.name` only if it clearly identifies the human contributor. Do
  not guess the author from the AI tool, shell username, email prefix, or code
  generation context. Use `@author TODO` and call it out if the human author is
  unclear.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
