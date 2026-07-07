# Security Policy

## Scope

This project is an academic SA62 Mobile Application Development CA repository for an AI-enabled wellness mobile app. Security review covers the implemented repository paths:

- `android-app/`
- `spring-backend/`
- `python-ai-service/`
- `dotnet-backend/`, when the optional backup API is included
- `desktop-app/`, when the optional desktop client is included
- `docker-compose*.yml`, Dockerfiles, GitHub workflow files, setup docs, and scripts

Do not include real passwords, JWT signing keys, internal service tokens, API keys, or private wellness data in issues, PRs, scan prompts, logs, screenshots, or committed files.

## Baseline Security Requirements

The project specs remain the source of truth. Security work must preserve these baseline rules:

- Android calls Spring Boot only; it must not call MySQL or the Python AI service directly.
- Spring Boot owns authentication, authorization, business rules, and MySQL writes.
- JWT protects all non-auth endpoints.
- Backend derives user identity from JWT claims, not client-supplied user ids.
- User-owned records, chats, and recommendations are always filtered by authenticated user.
- Passwords are hashed and never logged.
- Backend-to-Python internal operations use an internal service token.
- AI must stay local/free; do not add paid or cloud-only LLM dependencies.

## Codex Security Scans

Assume the Codex Security plugin is installed in the development environment. Use it as a review aid before merging security-sensitive changes and before final submission.

### Pull Request Diff Scan

Run a Codex Security diff scan for PRs that change:

- authentication, JWT, password handling, authorization, ownership checks, or user identity logic
- Spring Boot controllers, services, repositories, filters, or security configuration
- Python RAG, agent, Ollama, Chroma, or backend callback logic
- Dockerfiles, Compose files, GitHub Actions, setup scripts, or environment handling
- Android token storage, API clients, logout, or authenticated navigation
- optional `.NET Backup API` or desktop client authentication/API parity

Suggested Codex prompt:

```text
Run a Codex Security diff scan for the current branch against develop.
Focus on JWT auth, user data ownership, secret handling, backend-to-Python trust boundaries, Docker/runtime configuration, and local-only AI requirements.
```

For local uncommitted work, use:

```text
Run a Codex Security diff scan for my working tree.
Focus on JWT auth, user data ownership, secret handling, backend-to-Python trust boundaries, Docker/runtime configuration, and local-only AI requirements.
```

### Repository Or Scoped Scan

Run a broader Codex Security scan:

- before a major milestone demo
- before final zip/video submission
- after large dependency, Docker, auth, or API rewrites
- when a reviewer suspects a cross-component issue that a diff scan may miss

Suggested full-repo prompt:

```text
Run a Codex Security repository scan for this repo.
Focus on the Android -> Spring Boot -> MySQL -> Python AI service architecture, JWT authorization, cross-user data isolation, internal service token usage, Docker configuration, secret handling, and the local-only Ollama/RAG requirement.
```

Suggested scoped prompt:

```text
Run a Codex Security scoped-path scan for spring-backend.
Focus on authentication, authorization, ownership checks, error handling, MySQL persistence, and internal Python service calls.
```

## Required Evidence

Every PR that runs a Codex Security scan should include:

- scan type: diff, repository, or scoped path
- scan scope and base branch or path
- date run
- finding summary
- links or paths to generated scan reports, if available
- fix summary for each accepted finding
- explicit rationale for any suppressed or deferred finding

PRs should not merge while a high or critical Codex Security finding remains valid and unfixed, unless the team records a clear academic/demo limitation and the finding does not compromise required demo data, secrets, or cross-user access.

## Reporting Issues

For this coursework repository, report security issues through the team GitHub issue tracker or PR review comments. Use private communication for anything containing secrets, tokens, or personal data.

When reporting an issue, include:

- affected component and file path
- requirement or task ID, where known
- reproduction steps or request example
- expected impact
- recommended fix, if known

## Pre-Submission Security Checklist

Before final submission:

- Run a Codex Security repository scan or targeted scans across changed runtime components.
- Confirm no `.env`, real credentials, JWT secrets, service tokens, or local database dumps are committed.
- Confirm JWT and ownership tests pass.
- Confirm Android cannot access MySQL or Python AI directly.
- Confirm Docker containers do not expose unnecessary secrets in logs or docs.
- Confirm local-only AI settings still use Ollama with `qwen2.5:1.5b` and `nomic-embed-text`.
