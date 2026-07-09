# 09 Test And Demo Plan

<!-- @author Tiong Zhong Cheng -->

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-17, REQ-18, REQ-19, REQ-20, all verification evidence |
| Primary audience | Whole team |
| Upstream specs | All prior specs |
| Downstream specs | Demo script, final submission checklist |

## Test Strategy

The project should test behavior across mobile, backend, database, RAG, agent workflow, and Docker integration. Tests should support confidence for the 15-minute demo rather than chase maximum coverage.

## Backend Tests

Auth:

- Register creates a user with hashed password.
- Duplicate email is rejected.
- Login returns JWT for valid credentials.
- Login rejects invalid credentials.
- Protected endpoints reject missing or invalid JWT.

Wellness records:

- Create record succeeds for authenticated user.
- List records returns only the authenticated user's records.
- Update record changes only owned records.
- Delete record deletes only owned records.
- Attempting to access another user's record returns not found or forbidden.

Chat:

- Chat endpoint validates non-empty question.
- Chat endpoint calls AI service.
- Chat message is saved after successful AI response.
- AI timeout returns controlled error.

Recommendations:

- Generate endpoint calls Python agent.
- Saved recommendation belongs to authenticated user.
- List recommendations returns newest first.

Optional account privacy (`REQ-23`):

- Export endpoint requires JWT.
- Export payload includes only the authenticated user's profile, wellness records, chat messages, and recommendations.
- Export payload excludes password hash, raw JWTs, internal service tokens, and other users' rows.
- Delete account requires JWT and deletes the authenticated user's dependent rows plus user row in one transaction.
- Delete account requires password reconfirmation for local accounts and works for Google-only accounts without an app password.
- A previous JWT for a deleted account no longer grants access to protected endpoints.
- Delete account does not delete another user's data.

Optional `.NET Backup API`:

- Status and health endpoints match Spring response shapes.
- JWT generation and validation use the same secret, expiry, and claims as Spring.
- BCrypt password hashes created by Spring or .NET can be verified by the other backend.
- User-owned record, chat, and recommendation queries filter by authenticated user id.
- Internal APIs reject missing or invalid `X-Internal-Service-Token`.
- CI runs `.NET` backup backend tests without heavyweight Ollama generation.

Optional .NET desktop client (`REQ-21`):

- `ApiClient` builds the documented routes and attaches `Authorization: Bearer <jwt>` on non-auth calls.
- DTOs (de)serialize the documented JSON shapes for auth, records, chat, and recommendations.
- Error responses are parsed into the standard error shape and surfaced as user-friendly messages.
- `dotnet build`/`dotnet test` run in CI without any LLM dependency.
- Distributable Windows/macOS executables are produced with `desktop-app/build-desktop.sh` (self-contained, single-file); cross-compiles from any host. See `desktop-app/README.md`.
- Packaged desktop builds can set `BACKEND_BASE_URL=https://sa62wellness.duckdns.org/`
  so the generated `appsettings.json` points at the DigitalOcean backend without
  requiring a launch-time environment variable.

## Python AI Tests

RAG:

- Knowledge base files can be loaded.
- Text is chunked with metadata.
- Embeddings are requested from Ollama abstraction.
- Retriever returns top matching chunks for a sample query.
- Chat prompt includes retrieved context and safety rules.

Agent:

- Sparse records choose tracking-focused recommendation.
- Low average sleep chooses sleep focus.
- Low exercise frequency chooses activity focus.
- Low mood score chooses stress and mood focus.
- Successful agent run saves recommendation through backend client.
- Backend or Ollama failure returns controlled error.

## Android Tests

Unit or instrumentation tests should cover:

- Login form validation.
- Register form validation.
- Wellness record form validation.
- Wellness record exercise type is selected from the predefined list rather than
  entered as free text.
- API client attaches JWT after login.
- Logout clears local JWT.
- Dashboard aggregation, weekly summary badges, and BMI derivation
  (`DashboardDataHelperTest`).
- Chat SSE wire-format parsing for every event type and malformed input
  (`ChatSseParserTest`).
- API request/response DTO construction, equality, and copy semantics
  (`ApiModelsTest`).
- User-facing API error-message mapping for HTTP and IO failures
  (`ApiErrorMessageTest`).

JVM unit tests run via `./gradlew :app:testDebugUnitTest`; line coverage is
emitted for SonarQube with `:app:createDebugUnitTestCoverageReport` (JaCoCo).

Manual QA should cover:

- Loading states.
- Empty states.
- Network error states.
- Expired token flow.
- Chatbot shows visible local-AI progress inside the pending assistant bubble
  while waiting for the first streamed token, then reuses that bubble for the
  streamed answer.
- Chatbot square Stop icon cancels an in-flight streamed answer, removes the
  unpersisted pending bubble, restores the question text, and returns the
  control to the normal Send button.
- Full demo flow on emulator or physical device.
- Physical device demo uses USB debugging, `adb reverse tcp:8080 tcp:8080`, and `WELLNESS_API_BASE_URL=http://127.0.0.1:8080/` rather than a committed LAN IP.
- Optional `REQ-23`: Profile opens Privacy screen; export opens a JSON share/save flow; delete cancel makes no request; confirmed delete signs out; offline export/delete shows friendly error; expired token returns to Login.
- Optional `REQ-22` + `REQ-23`: Google-only accounts can export data, delete the account from Privacy, and reactivate a deactivated account by signing in with Google again only after accepting the reactivation confirmation dialog.
- Optional `S-01`: Android notification permission is requested where required;
  manually generated recommendations send a local generated-insight broadcast;
  the scheduled local broadcast can detect a newer recommendation after the
  demo-friendly first poll of about 30 seconds, then repeat about every 2
  minutes, and post a notification that opens the Recommendations screen.
- Android background AI UX: start a chat stream and a recommendation generation,
  press Home while each is in flight, then reopen the app and confirm the request
  was not cancelled merely because the app was minimised. If Android recreated
  the screen, confirm the saved chat/recommendation reloads from Spring Boot.

## Docker Smoke Tests

Minimum smoke checks:

- `mysql` container becomes healthy.
- `spring-backend` starts and exposes health endpoint.
- `python-ai-service` starts and exposes health endpoint.
- `ollama` container starts.
- Required Ollama models are present before demo.
- Spring Boot can connect to MySQL.
- Spring Boot can reach Python AI service.
- Python AI service can reach Ollama.
- Android physical-device debug build can be installed through `tools/scripts/android-phone-demo.sh`.
- Optional backup stack exposes `.NET Backup API` on `http://localhost:8082` and can be smoke-tested with the same contract script by setting `BASE_URL`.

## Codex Security Review

Codex Security scans are review evidence, not a replacement for the functional tests above. Follow the workflow in `SECURITY.md`.

Minimum security review evidence:

- Codex Security diff scan for PRs that change auth, authorization, user data ownership, secret handling, Docker/runtime configuration, Android token storage, Python callbacks, or optional backup/desktop API parity.
- Codex Security repository or scoped-path scan before final submission, covering changed runtime components.
- Valid high or critical findings fixed before merge/submission, or explicitly deferred with rationale that does not compromise required demo data, secrets, or cross-user access.
- PR or submission notes include scan type, scope, date, finding summary, fix summary, report path if available, and accepted suppressions.

## Demo Data

Demo data should be populated through repeatable backend-controlled seed logic or a documented API script. It must not require Android to write directly to MySQL or require manual row editing in Adminer.

Prepare one demo user:

- Email: `demo@wellness.test`
- Password: documented only in local demo notes, not committed as a secret.

Prepare at least five wellness records showing trends:

- Two low-sleep days.
- Two exercise days.
- Mixed mood scores.
- One note mentioning stress or tiredness.

Prepare RAG questions:

- "How can I improve my sleep routine?"
- "What kind of exercise should I do when I feel tired?"
- "How can I manage stress during a busy week?"

Seed/reset acceptance:

- `tools/scripts/seed-data.sh` can populate the demo user and trend records through backend APIs.
- Running the seed process twice must not create uncontrolled duplicate demo users.
- The team can reset demo data before rehearsal.
- Demo data supports but does not replace showing at least one live create, update, and delete action.

## 15-Minute Demo Script

Suggested timing:

| Time | Segment | Evidence |
| --- | --- | --- |
| 0:00-1:00 | Introduce problem and architecture | Architecture diagram |
| 1:00-2:30 | Login and JWT-secured app flow | Login screen, backend protected APIs |
| 2:30-5:00 | Wellness record CRUD | Create, view, update, delete record |
| 5:00-7:30 | MySQL persistence | Data remains after refresh, optional DB inspection |
| 7:30-10:00 | RAG chatbot | Ask wellness question, show answer and sources |
| 10:00-12:00 | Agentic recommendation | Generate recommendation from recent records |
| 12:00-13:30 | Docker and local AI | Show Compose services and Ollama models |
| 13:30-14:30 | GitHub collaboration and CI | PR workflow, Actions summary |
| 14:30-15:00 | Wrap up | Requirements mapping and team contributions |

## Video Demo Flow Plan

Target runtime: **13:30 to 14:45**, leaving a safety buffer under the 15-minute limit. This is a **submission video**, not a live presentation script. The recording may use short edited clips, prepared seed data, completed AI responses, and cutaways to code/tests so the team can show the broadest feature set clearly. Any edited wait time should be truthful: show the action starting, then cut to the completed result or state that the generation wait was shortened.

### Pre-Recording Setup

- Start the local demo stack before recording: MySQL, Spring Boot, Python AI service, Ollama, and any required vector persistence.
- Pull/verify Ollama models before recording: `qwen2.5:1.5b` and `nomic-embed-text`.
- Reset and seed demo data through backend-controlled seed logic or the documented API script.
- Prepare Android emulator or physical device with the app installed and backend base URL confirmed.
- Prepare one browser/terminal tab showing Docker Compose service health, one tab for GitHub Actions/PR evidence, and optional Adminer/database inspection.
- Prepare short clips/screenshots for optional showcase features such as Privacy export/delete, Google SSO, .NET desktop client, .NET Backup API, SonarQube, DigitalOcean deployment, or Figma UI evidence if they are stable.
- Use a throwaway user only for destructive account-delete demonstration if optional `REQ-23` is shown.

### Recording Storyboard

| Timebox | Shot / Screen | Presenter Action | Narration Focus | Requirement Evidence |
| --- | --- | --- | --- | --- |
| 0:00-0:35 | Title slide plus finished app montage | Show 3-4 quick cuts: Dashboard, Chatbot, Recommendation, Privacy/Profile | Start with the value proposition: private local AI wellness tracking with Android, Spring Boot, MySQL, RAG, and an agentic recommendation workflow. | REQ-01, REQ-08, REQ-09, REQ-11, REQ-13 |
| 0:35-1:25 | Architecture diagram | Highlight Android -> Spring Boot -> MySQL/Python -> Ollama | Establish component boundaries and local/free AI design before showing features. | REQ-08, REQ-09, REQ-12, REQ-14 |
| 1:25-2:20 | Android auth clips | Show register/login success, JWT-protected redirect or logout, optional Google SSO if stable | Authentication, JWT security, and optional SSO polish. | REQ-02, REQ-03, REQ-22, NFR-02 |
| 2:20-4:20 | Dashboard and wellness CRUD montage | Show dashboard metrics, add, edit, delete confirmation, empty/error/loading snippets if captured | Core mobile CRUD and polished Android XML UI states. | REQ-01, REQ-04, REQ-05, REQ-06, REQ-07, NFR-04 |
| 4:20-5:05 | Persistence/ownership evidence | Cut to MySQL/Adminer or API response showing the saved record belongs to the user | Backend-owned persistence and user data isolation; Android does not access MySQL directly. | REQ-09, NFR-01 |
| 5:05-6:55 | RAG chatbot showcase | Show a prepared question, retrieved sources/response, and optionally a streaming token clip | Local RAG turns wellness KB plus recent context into a grounded answer. | REQ-10, REQ-11, REQ-12 |
| 6:55-8:40 | Agentic recommendation showcase | Show Generate, then cut to completed recommendation with trend summary/action items; optionally show deterministic rule test output | Python agent retrieves records, analyses trends, generates, and saves a recommendation. | REQ-13 |
| 8:40-9:55 | Privacy and data controls | Show Privacy screen, export JSON flow, and delete confirmation using a throwaway account or cancelled confirmation | Makes local/private architecture visible and gives account data controls. | REQ-23, S-03 |
| 9:55-10:55 | Docker/local runtime evidence | Show Compose services, health endpoints, Ollama models, and Chroma/vector persistence if visible | Demo stack is reproducible and local/free. | REQ-12, REQ-14, NFR-03 |
| 10:55-11:55 | Quality and CI evidence | Show GitHub Actions, test commands/results, SonarQube or Codex Security evidence if available | Collaboration and validation evidence; CI avoids heavyweight LLM generation. | REQ-16, NFR-05 |
| 11:55-12:55 | Optional bonus showcase | Use fast cuts for stable bonus features: .NET desktop client, .NET Backup API health, DigitalOcean HTTPS health, Figma UI spec, or Google SSO | Show extra polish without replacing mandatory Android/Spring/Python evidence. | REQ-21, REQ-22, REQ-16 |
| 12:55-14:15 | Requirement mapping and team contribution close | Show traceability matrix, submission checklist, and team ownership summary | Tie the video back to assignment requirements, author comments, and final zip readiness. | REQ-17, REQ-18, REQ-19 |

### Presenter Script Cues

- Keep each segment anchored to the marking criteria: Android UI, backend integration, database persistence, AI/RAG, agentic workflow, Dockerisation, and collaboration evidence.
- Say "Android calls Spring Boot" during every data/AI segment where boundaries matter.
- Say "local/free Ollama" when showing chatbot and recommendations.
- Say "prepared seed data makes the video reliable; the recording still shows the feature behavior and backend evidence" before or during the CRUD segment.
- Use fast cuts for repeated screens; spend narration time on features, integration, and evidence rather than waiting for loading screens.
- When showing optional features, label them as optional or stretch so they do not appear to replace mandatory scope.

### Edited-Video Guidelines

- Use cuts to remove typing, model waits, Docker startup time, Gradle/Maven build time, and repeated navigation.
- Do not fake feature output. If a response or recommendation was pre-generated, say it is a saved result from the same local stack.
- Prefer split-screen or picture-in-picture only when it improves clarity, for example Android action on one side and backend/log evidence on the other.
- Avoid showing secrets, raw JWTs, `.env` contents, private keys, or real personal data.
- Keep optional features to short proof clips unless they are unusually polished and stable.

### Recording Fallbacks

- If Ollama generation is slow, show the request starting, then cut to the completed response and mention that the generation wait was shortened for the recording.
- If live chat fails during recording, use a saved chat response from the seeded account after showing service health or test evidence; do not present it as a fresh live result.
- If the recommendation agent times out, show a saved recommendation plus deterministic trend-rule test evidence, then state the live limitation.
- If database inspection is slow, use app refresh/persistence plus a short API response as persistence evidence.
- If optional privacy export/delete is unstable, show the Privacy screen statement and export/delete UI only; skip destructive actions.
- If a bonus feature is unstable, replace it with a short spec/traceability or test-evidence shot rather than letting it consume the video.

### Final Recording Checklist

- Video is under 15 minutes.
- Login, CRUD, chatbot, recommendation, Docker/local AI, and CI evidence are all visible.
- At least one stretch/bonus feature is shown if stable, with priority order: Privacy controls, Google SSO, .NET desktop client, .NET Backup API, SonarQube/DigitalOcean evidence.
- No real secrets, JWTs, passwords, API keys, or private `.env` contents are shown.
- Destructive delete, if shown, uses a throwaway account.
- Narration includes the requirement IDs or a final mapping slide/table.
- Final clip is reviewed once at normal speed before submission.

Optional backup demo note:

- If time permits, briefly show `.NET Backup API` health on port `8082` as cold-standby evidence. Do not spend core demo time on backup routing unless the mandatory Spring, Android, MySQL, RAG, and Python agent flows are already complete.

Optional desktop demo note:

- If time permits, briefly show the .NET Avalonia desktop client logging in and reading the same wellness records as Android against the same Spring Boot backend, as cross-platform bonus evidence (`REQ-21`). Do not let it displace the mandatory Android flow.

Optional privacy demo note:

- If time permits, briefly show the Privacy screen's local AI statement, export-data action, and delete-account confirmation as private/standout evidence (`REQ-23`). Use a throwaway demo account for confirmed deletion so the core seeded demo flow remains intact.

## Submission Checklist

- Integrated solution code included.
- Specs and diagrams included.
- Video demo included and kept near 15 minutes.
- Author comments added to classes or key methods.
- `.env.example` included but real `.env` excluded.
- README/setup instructions included.
- Codex Security final scan evidence included or referenced.
- One zipped file named with team name.

## Acceptance Criteria

- Test plan covers mandatory CA features.
- Demo script fits within 15 minutes.
- Demo explicitly shows Android, backend, MySQL, RAG chatbot, JWT, Python agent, and Dockerisation.
- Security review evidence shows Codex Security scans were run for security-sensitive changes and final runtime components.
- Optional AWS is mentioned only if it exists and is stable.
