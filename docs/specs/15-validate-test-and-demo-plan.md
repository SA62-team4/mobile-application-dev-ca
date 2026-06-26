# 09 Test And Demo Plan

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
- API client attaches JWT after login.
- Logout clears local JWT.

Manual QA should cover:

- Loading states.
- Empty states.
- Network error states.
- Expired token flow.
- Full demo flow on emulator or device.

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

## Demo Data

Demo data should be populated through repeatable backend-controlled seed logic or a documented API script. It must not require Android to write directly to MySQL or require manual row editing in Adminer.

Prepare one demo user:

- Email: `demo@example.com`
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

## Submission Checklist

- Integrated solution code included.
- Specs and diagrams included.
- Video demo included and kept near 15 minutes.
- Author comments added to classes or key methods.
- `.env.example` included but real `.env` excluded.
- README/setup instructions included.
- One zipped file named with team name.

## Acceptance Criteria

- Test plan covers mandatory CA features.
- Demo script fits within 15 minutes.
- Demo explicitly shows Android, backend, MySQL, RAG chatbot, JWT, Python agent, and Dockerisation.
- Optional AWS is mentioned only if it exists and is stable.
