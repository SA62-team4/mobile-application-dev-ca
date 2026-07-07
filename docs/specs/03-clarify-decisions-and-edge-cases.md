# 12 Clarification Log

<!-- @author Tiong Zhong Cheng -->

## Purpose

This file records resolved ambiguities, dependencies, assumptions, and edge cases. It corresponds to the Spec Kit **Clarify** step.

Use it whenever the team asks, "What did we decide?" or "Is this in scope?"

## Resolved Decisions

| ID | Decision | Rationale | Affects |
| --- | --- | --- | --- |
| DEC-001 | Team size is planned as 7 people. | User provided team size. | Ownership, task breakdown |
| DEC-002 | Android UI uses XML layouts, not Jetpack Compose. | User selected XML layouts. | Android spec, UI tasks |
| DEC-003 | AI uses basic full RAG, not prompt-only chatbot. | User requested full RAG but basic. | RAG spec, chatbot tasks |
| DEC-004 | LLM must be free/local. | User required local/free AI. | RAG, agent, Docker |
| DEC-005 | Ollama is the default local LLM runtime. | Easiest local setup for team demo. | RAG, agent, Docker |
| DEC-006 | Use curated wellness KB rather than document upload. | More reliable for CA demo and marking. | RAG spec |
| DEC-007 | Dockerise backend/runtime services where practical. | User requested Dockerisation. | Docker spec, roadmap |
| DEC-008 | Android remains outside Docker. | Android Studio/emulator workflow is more practical. | Docker spec |
| DEC-009 | AWS is optional hybrid staging, not main demo path. | Local Ollama demo is more reliable and lower risk. | DevOps, demo plan |
| DEC-010 | Use PlantUML instead of Mermaid for diagrams. | Mermaid preview failed in local editor. | Docs, diagrams |
| DEC-011 | Use PlantUML server mode in VS Code workspace settings. | User's preview extension required `plantuml.server`. | `.vscode/settings.json` |
| DEC-012 | Production cloud target is a DigitalOcean Droplet running Docker Compose, with Ollama on-server, HTTPS via Caddy, Terraform-managed infra, GitHub Actions deploy, and secrets in GitHub Actions secrets. | DO Droplet + Compose is the simplest path that still hosts the local LLM; resolves the prior AWS-vs-other ambiguity. | `10-plan-docker-devops.md`, `infra/terraform/`, deploy workflows |
| DEC-013 | Google SSO is an additional login path. Android obtains a Google ID token; the backend verifies it (signature via Google JWKS, audience = our Web Client ID, issuer = accounts.google.com) and then issues the same internal HMAC JWT as email/password login. | One consistent token format for all API calls regardless of login method; keeps email/password and JWT model unchanged. | `06-plan-api-contracts.md`, `07-plan-android-ui-flows.md`, `05-plan-backend-data-model-erd.md`, `10-plan-docker-devops.md` |
| DEC-014 | SSO-provisioned users have a null `password_hash`. New Google users are auto-provisioned on first login by email; an existing email/password user signing in with Google reuses the same account. | SSO users have no local password; matching by email avoids duplicate accounts. | `05-plan-backend-data-model-erd.md`, `06-plan-api-contracts.md` |

## Open Questions

| ID | Question | Default If Unanswered | Needed By |
| --- | --- | --- | --- |
| Q-001 | What is the final team name for zip submission? | Use placeholder `TeamName.zip` in docs until known. | Submission |
| Q-002 | Will AWS actually be used or only discussed? | Treat AWS as optional architecture discussion. | DevOps, demo |
| Q-003 | Will the team use Maven or Gradle for Spring Boot? | Use Maven unless implementation team prefers Gradle before scaffold. | Backend scaffold |
| Q-004 | What minimum Android SDK should be used? | Choose a common stable minimum during Android scaffold. | Android scaffold |
| Q-005 | Which exact author format should classes use? | Use `@author Name` comments unless lecturer provides another format. | Implementation |

## Edge Case Decisions

| Area | Edge Case | Decision |
| --- | --- | --- |
| Auth | Logout with stateless JWT | Android clears token; backend logout endpoint returns success for UX. |
| Auth | Expired token | Android returns user to login and clears stored token. |
| Auth | Invalid/expired/wrong-audience Google ID token | Backend returns `401`; Android shows a sign-in error and stays on login. |
| Auth | Google sign-in returns no ID token (misconfigured Web Client ID) | Android shows a configuration error; no backend call is made. |
| Auth | Google account email matches an existing email/password user | Reuse the existing account; do not create a duplicate. |
| Auth | SSO user (null password) attempts email/password login path | BCrypt match against the null/empty hash always fails; login is rejected. |
| Records | User guesses another record id | Backend returns not found or forbidden, never the other user's data. |
| RAG | Ollama unavailable | Show friendly retry/error message; do not fake a saved AI response. |
| RAG | No matching KB chunks | Give cautious wellness guidance and note limited available context. |
| Agent | Fewer than 3 records | Generate tracking-focused recommendation. |
| Docker | Ollama slow in CI | CI uses health/mocked checks, not heavyweight generation. |

## Clarification Workflow

When ambiguity appears:

1. Add the question to Open Questions.
2. Resolve it with the team or user.
3. Move it to Resolved Decisions.
4. Update affected specs and traceability rows.

