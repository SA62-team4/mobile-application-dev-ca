# 01 Project Requirements

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Owner | Whole team |
| Last reviewed | Not yet reviewed |
| Depends on | Assignment PDF |
| Feeds | Architecture, ERD, API, Android UI, RAG, agent, Docker, test specs |

## Assignment Goal

Build a simple AI-enabled wellness mobile application for the SA62 Mobile Application Development Continuous Assessment. The application must demonstrate mobile app development, backend integration, database persistence, prompt engineering or RAG, and end-to-end system integration.

## Requirement IDs

Mandatory functional requirements:

| ID | Requirement |
| --- | --- |
| REQ-01 | Android app uses Kotlin with XML layouts. |
| REQ-02 | User can register, log in, and log out. |
| REQ-03 | JWT protects all non-auth APIs. |
| REQ-04 | User can create wellness records. |
| REQ-05 | User can retrieve current and historical wellness records. |
| REQ-06 | User can update wellness records. |
| REQ-07 | User can delete wellness records. |
| REQ-08 | Backend uses Java Spring Boot. |
| REQ-09 | Data is stored and retrieved from MySQL through backend services. |
| REQ-10 | Chatbot accepts questions from Android through backend. |
| REQ-11 | Chatbot uses basic local RAG. |
| REQ-12 | LLM is free/local only. |
| REQ-13 | Python agentic AI retrieves data, analyses trends, generates and saves recommendations. |
| REQ-14 | Backend/runtime services are Dockerised where practical. |
| REQ-15 | ERD documents backend data model. |
| REQ-16 | GitHub collaboration and CI are defined. |
| REQ-17 | Video demo fits assignment expectations. |
| REQ-18 | Author is indicated in classes or key methods. |
| REQ-19 | Single integrated zipped submission is produced. |
| REQ-20 | Demo-ready mock data can be populated into MySQL through backend-controlled seed logic. |

Cross-cutting non-functional requirements:

| ID | Requirement |
| --- | --- |
| NFR-01 | App prevents one user from accessing another user's data. |
| NFR-02 | Errors are user-friendly and do not expose stack traces. |
| NFR-03 | Demo works without paid services. |
| NFR-04 | Major screens show loading, empty, success, and error states. |
| NFR-05 | CI avoids heavyweight local LLM generation. |

## Planned Feature Set

Mandatory features:

- Register, login, and logout.
- Secure authenticated API access using JWT.
- Create wellness records.
- View current and historical wellness records.
- Update wellness records.
- Delete wellness records.
- Ask the RAG chatbot wellness-related questions.
- Generate personalised wellness recommendations using a Python agent.
- Save and retrieve chatbot messages and recommendations.
- Populate mock/demo data for a predictable presentation flow.

Optional enhancements:

- AWS hybrid staging environment.
- Adminer or phpMyAdmin for database inspection during demo.
- Scheduled agent recommendation generation.
- Rendered PNG/PDF exports of PlantUML diagrams for presentation slides.

## Mock Data Requirement

The implementation must provide a safe way to populate demo data into the MySQL database for local development and presentation rehearsal.

Seed data must include:

- At least one demo user with a documented local-only password.
- At least five wellness records showing sleep, exercise, mood, and notes trends.
- Optional saved chat messages that demonstrate RAG responses.
- Optional saved recommendations that demonstrate the Python agent output.

Rules:

- Mock data must be clearly separated from production/runtime logic.
- Mock data must be loaded through backend-owned logic, migrations, profile-based seeders, or documented API scripts, not by Android writing directly to MySQL.
- Mock credentials must be safe for local demo only and must not reuse real passwords.
- Seed scripts must be repeatable or idempotent so the team can reset the demo without manual database editing.
- Demo data must not replace the need to show create, update, and delete flows during the presentation.

## Team Ownership

The project assumes a 7-person team.

| Member | Primary Ownership | Supporting Work |
| --- | --- | --- |
| 1 | Android authentication, navigation, JWT storage, logout | UI consistency checks |
| 2 | Android wellness record list and CRUD forms | Form validation states |
| 3 | Android chatbot and recommendation screens | Demo screenshots |
| 4 | Spring Boot auth, JWT security, user APIs | Security tests |
| 5 | Spring Boot wellness, chat, recommendation APIs, MySQL schema, ERD | API documentation |
| 6 | Python RAG service, curated knowledge base, Chroma, Ollama integration | RAG smoke tests |
| 7 | Agentic AI workflow, Docker Compose, GitHub Actions, demo coordination | Integration test script |

## Marking Criteria Mapping

| Assessment Area | Planned Evidence |
| --- | --- |
| Requirements fulfilment | All mandatory app, backend, database, chatbot, JWT, and Python agent features included |
| Functionality and integration | Android calls backend, backend persists to MySQL, backend calls Python AI service |
| Code quality | Layered packages, DTOs, services, repositories, tests, author comments |
| UI and UX | Clear XML screens, bottom navigation, loading and error states |
| System design | Architecture diagrams, ERD, API spec, Docker plan |
| Bonus or advanced features | Basic local RAG, Dockerisation, optional AWS staging, agentic recommendations |

## Definition Of Done

- User can register, log in, and log out.
- Logged-in user can create, view, update, and delete their own wellness records.
- Chatbot answers wellness questions using local RAG and stores chat history.
- Agent retrieves recent records, analyses trends, generates a recommendation, saves it, and displays it in the app.
- Backend data is stored in MySQL.
- JWT protects all non-auth endpoints.
- Docker Compose can run backend services needed for demo.
- Mock data can be populated for a reliable demo and reset/re-run without manual database edits.
- Specs, ERD, API documentation, setup notes, and demo script are complete.
