# 16 Kanban Sprint Board

<!-- @author Abu Bakar Nasir, Tang Chee Seng -->

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Purpose | Sprint plan and Kanban board view layered over the task backlog |
| Controls | Scheduling and execution view only — does not introduce new scope |
| Primary audience | Whole team, scrum/board owner |
| Upstream specs | `02-specify-project-requirements.md`, `11-plan-implementation-roadmap.md`, `12-tasks-implementation-backlog.md`, `13-analyze-traceability-matrix.md` |
| Companion file | `16-kanban-sprint-board.csv` (importable into Trello / Jira / GitHub Projects) |

## Purpose

This file is the **execution view** of the project: it takes the implementation-ready units from `12-tasks-implementation-backlog.md`, reconciles them with the **actual code state**, and arranges the remaining work into **5-day sprints** that hit the **9 Jul 11pm** deadline. It also evaluates the design against five business features and records a stretch backlog for the gaps.

It does **not** invent new mandatory scope. Every committed card traces to a `T-*` task and a `REQ-*`/`NFR-*` requirement. New stretch cards use the `S-*` prefix and are explicitly optional.

## How To Use This Board

1. Import `16-kanban-sprint-board.csv` into your tool (Trello: *Create board → import CSV*; Jira: *Import issues from CSV*; GitHub Projects: *paste/import*).
2. Map the `Status` column to board columns and the `Sprint` column to a sprint field or label.
3. Map `Owner` (M1–M7) to assignees, and `Epic` to labels or swimlanes.
4. Pull stretch (`S-*`) cards in **only** when a sprint is ahead of its committed cards.

## Where The Code Actually Stands (drives the schedule)

| Component | State | Remaining long pole |
| --- | --- | --- |
| Spring backend | ~80% — BCrypt, JWT filter, 6 controllers (Auth/Wellness/Chat/Recommendation/Internal/Status), repositories, DTOs | Tests (auth, ownership, CRUD), verify chat/agent orchestration, seed data |
| Python AI | ~60% — `rag_service`, `agent_service`, `ollama_client`, `backend_client`, KB (5 files) | Verify Chroma indexing + Ollama embeddings, agent rule tests |
| Android | ~25% — auth shell only: login/register/home layouts, `TokenStore`, `ApiClient`/`ApiService` | **Wellness CRUD, chat, recommendations screens — the critical path** |
| Docker / DevOps | Compose (mysql, ollama, python-ai, spring, adminer) + phone-demo script | Full-stack smoke, CI green |

**Critical path = Android UI** (CRUD → chat → recommendations). Backend and Python mostly need verification and tests, not net-new build.

## Kanban Columns

Match the status values already defined in `12-tasks-implementation-backlog.md`:

`Backlog → Ready → In Progress → In Review → Done`  ·  `Blocked` applied as a tag, not a column.

## Epics (Swimlanes)

| Epic | Maps To | Business Feature Served |
| --- | --- | --- |
| E1 Auth & Security | REQ-02, REQ-03, NFR-01 | Secure |
| E2 Wellness CRUD | REQ-04..07, REQ-09 | Comprehensive (core) |
| E3 RAG Chatbot | REQ-10, REQ-11, REQ-12 | Comprehensive |
| E4 Agentic AI | REQ-13 | Standout |
| E5 DevOps / Docker / CI | REQ-14, REQ-16, NFR-03, NFR-05 | Enabler |
| E6 Demo / Seed / Submission | REQ-17, REQ-18, REQ-19, REQ-20 | Enabler |
| E7 Stretch — Business-Feature Gaps | New `S-*` cards | Timely / Comprehensive / Private / Secure / Standout |

**Card sizing:** story points `1 / 2 / 3 / 5 / 8`. Team capacity ≈ 7 people × 5 days ≈ ~25 effective person-days per sprint.

## Sprint Schedule

Today is **Sun 28 Jun 2026**; deadline **Thu 9 Jul 11pm**; presentation **Fri 10 Jul**. Two clean 5-day sprints plus a 1-day hardening window fit exactly.

| Sprint | Dates | Goal (demoable increment) | Points |
| --- | --- | --- | --- |
| Sprint 1 — Core vertical slice | Mon 29 Jun → Fri 3 Jul | Login → create/list/edit/delete wellness records → persists in MySQL, end-to-end on a device | 25 |
| Sprint 2 — AI features | Sat 4 Jul → Wed 8 Jul | Chat (with sources) + generate recommendation from real trends; full Docker stack + CI green | 28 |
| Hardening + Submit | Thu 9 Jul (submit by 11pm) | Author comments, rehearsed 15-min demo, `TeamX.zip`, peer evaluation | 7 |
| Present | Fri 10 Jul | — | — |

> Weekend (Sat 4 / Sun 5 Jul) sits inside Sprint 2 — shift those cards earlier if the team does not work weekends.

### Sprint 1 — Core vertical slice (29 Jun → 3 Jul)

| Card | Title | Epic | Owner | Pts | Status | Depends On | REQ | Done When |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T-205 | Auth/JWT test suite green (register hashes, dup-email reject, login ok/reject, missing-token reject) | E1 | M4 | 3 | Ready | — | REQ-02, REQ-03 | Tests pass in CI |
| T-204 | Ownership guards + cross-user access tests | E1 | M4 | 3 | Done | T-205 | NFR-01 | Foreign record returns 403/404 |
| T-301t | Wellness CRUD API tests (create/list/update/delete, owned-only) | E2 | M5 | 3 | Backlog | T-205 | REQ-04..07, REQ-09 | Tests pass |
| T-303 | **Android wellness CRUD screens** (list, add/edit form + validation, delete-confirm; loading/empty/error states) | E2 | M2 | 8 | Backlog | T-301t | REQ-04..07, NFR-04 | CRUD works on device end-to-end |
| T-302 | Android auth polish: logout clears token, expired-token → login redirect | E1 | M1 | 3 | Ready | — | REQ-02, NFR-04 | Logout + expiry verified |
| T-403 | Verify Python RAG: KB chunked, Chroma index built, Ollama embeddings return | E3 | M6 | 3 | Ready | — | REQ-11, REQ-12 | Reindex + retrieval smoke pass |
| T-601a | Docker smoke: mysql + spring up, record persists across restart | E5 | M7 | 2 | Ready | — | REQ-14 | Persistence verified |

**Sprint 1 exit demo:** a logged-in user performs full CRUD; data survives a refresh/restart.

### Sprint 2 — AI features (4 Jul → 8 Jul)

| Card | Title | Epic | Owner | Pts | Status | Depends On | REQ | Done When |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T-404 | Verify/finish backend chat orchestration + tests (validate question, call AI, save msg+sources, timeout error) | E3 | M5 | 3 | Backlog | T-301t | REQ-10, REQ-11 | Chat API saves answer + sources |
| T-405 | **Android chatbot screen + history** (send, answer + source chips, AI-waiting/error states) | E3 | M3 | 5 | Backlog | T-404 | REQ-10, NFR-04 | Chat works from app |
| T-502 | Agent deterministic trend-rule tests (sparse→track, low-sleep→sleep, low-exercise→activity, low-mood→stress) | E4 | M7 | 3 | Backlog | — | REQ-13 | Rule tests pass |
| T-503 | Verify RAG-assisted recommendation generation saved through backend internal API | E4 | M7 | 3 | Backlog | T-403, T-502 | REQ-13 | Recommendation row saved |
| T-504 | **Android recommendations screen** (generate button, trend summary + 3 actions, list newest-first) | E4 | M3 | 5 | Backlog | T-503 | REQ-13, NFR-04 | Recommendation visible in app |
| T-601b | Full-stack Docker smoke (mysql + spring + python + ollama reachable, models present) | E5 | M7 | 3 | Backlog | T-601a | REQ-14, NFR-03 | All health checks green |
| T-602 | CI green: build + test Android/Spring/Python; mock heavyweight LLM | E5 | M7 | 3 | Backlog | T-601b | REQ-16, NFR-05 | Actions pass on PR |
| T-603 | Repeatable seed data + 15-min demo script | E6 | M7 + all | 3 | Backlog | T-303, T-405, T-504 | REQ-17, REQ-20 | Reseed idempotent; rehearsal fits 15m |

**Sprint 2 exit demo:** chat answers a wellness question with sources; the agent generates a recommendation from seeded trends.

### Hardening + Submit (9 Jul)

| Card | Title | Epic | Owner | Pts | Depends On | REQ | Done When |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T-604 | Author `@author` comments, README/setup, `.env.example` check, build `TeamX.zip` | E6 | all | 3 | T-603 | REQ-18, REQ-19 | Zip + docs ready |
| T-605 | Full 15-min rehearsal on clean machine + record video | E6 | all | 3 | T-604 | REQ-17 | Video recorded under 15m |
| T-606 | Peer-evaluation submission | E6 | all | 1 | — | Peer eval (5 marks) | Submitted |

## Business-Feature Evaluation

Does the current spec + code meet the five key business features?

| # | Feature | Verdict | Evidence | Gap |
| --- | --- | --- | --- | --- |
| 1 | **Secure** — are my login details protected? | Meets | BCrypt hashing (`SecurityConfig`), JWT filter, REQ-02/03, ownership NFR-01, internal service token, no committed secrets, per-account login throttling with `429` + `Retry-After` (S-04), TLS-only base network config with a local-host exception | Lockout counters are in-memory and per-instance, so a restart or a second replica clears them; stateless logout relies on client-side token clear |
| 2 | **Private** — what leaves my control? | Meets — strongest asset | 100% local AI: Ollama + Chroma + curated KB (DEC-004, REQ-12); data path Android → Spring → Python → Ollama stays on-machine; nothing sent to external servers | Not surfaced to the user; no data export/delete; health-data consent/retention not stated |
| 3 | **Timely** — informed as soon as it happens? | Does not meet (weakest) | Scheduled agent is *optional* in `09-plan-agentic-ai-workflow.md` and not implemented; no `@Scheduled`, no push/local notifications | User only learns of a new recommendation by opening the app and tapping *Generate* |
| 4 | **Comprehensive** — what value do I get? | Partial | CRUD + RAG chat + agentic recommendations = real value | Only flat lists; the agent computes trends but the user never *sees* them — no insights/dashboard, goals, or streaks |
| 5 | **Standout** — what makes it different? | Latent | Local-private RAG + autonomous trend-analysing agent is uncommon for a student app | Not framed as the differentiator; without proactivity (#3) it reads like a normal chatbot |

**Headline:** strong on Secure + Private (Private is the best marketing angle), solid-but-flat on Comprehensive, under-sold on Standout, and genuinely missing Timely.

## Stretch Backlog — E7 (pull only if ahead)

All stretch cards stay **local-only and demo-safe** per the constitution (no paid/cloud AI, demo reliability first).

| Card | Gap → Feature | What To Build | Owner | Pts |
| --- | --- | --- | --- | --- |
| S-01 | Timely | Spring `@EnableScheduling` + `@Scheduled` agent run when backend-side scheduling is added; Android local notification via simple `AlarmManager`/broadcast polling for a "new recommendation". Makes the agent *proactive* — fixes the weakest feature and elevates Standout | M7 + M1 | 5 |
| S-02 | Comprehensive | Android trends dashboard: sleep/exercise/mood summary tiles or simple charts over recent records (reuses data the agent already analyses) | M2 | 5 |
| S-03 | Private + Standout | In-app "your data stays on this device — AI runs locally" Privacy screen plus Spring-owned account export/delete endpoints (`REQ-23`, `T-901`..`T-903`). Converts the privacy architecture into visible marks | M4 + M1 | 3 |
| S-04 | Secure | Per-account login throttling/lockout returning `429` + `Retry-After` in Spring and the .NET backup (`T-1001`), Android lockout and session-expired banners (`T-1002`..`T-1003`), release build type on the strict network security config. **In review** — Spring `LoginAttemptService` unit test still outstanding (`T-1004`) | M4 + M1 | 3 |
| S-05 | Standout | Frame the local-AI + agentic combo in the demo intro + a one-screen "how it works (private, local AI coach)". Low effort, high marking value under System Design / Bonus | all | 1 |

## Other Considerations (notes, not cards)

- Android offline / no-network caching behaviour.
- First-run onboarding / empty-state experience (partly covered by NFR-04).
- Chat input scope-guard (prompt-injection — partly covered by the RAG prompt rules in `08`).
- Accessibility: content descriptions, font scaling — feeds the UI/UX marking criterion.
- Wellness input range validation (sleep 0–24h, non-negative exercise minutes, mood bounds).
- Resolve open questions `Q-001..Q-005` in `03-clarify-decisions-and-edge-cases.md` (team name for the zip, Maven vs Gradle, minimum Android SDK, author-comment format).

## Demo-Coverage Check

The three sprint-exit demos together cover every mandatory CA feature, matching the 15-minute script in `15-validate-test-and-demo-plan.md`:

| Mandatory feature | Covered by |
| --- | --- |
| Login + JWT-secured app | Sprint 1 (T-302, T-205/T-204) |
| Wellness CRUD + MySQL persistence | Sprint 1 (T-303, T-301t, T-601a) |
| RAG chatbot through backend | Sprint 2 (T-404, T-405, T-403) |
| Agentic recommendation | Sprint 2 (T-502, T-503, T-504) |
| Dockerisation + local AI | Sprint 2 (T-601b, T-602) |
| Integrated solution, video, author comments | Hardening (T-603, T-604, T-605) |
