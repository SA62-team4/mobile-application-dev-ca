# 02 System Architecture

<!-- @author Tiong Zhong Cheng -->

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-01, REQ-08, REQ-09, REQ-10, REQ-12, REQ-13, REQ-14, REQ-16, REQ-22, REQ-23, NFR-03 |
| Primary audience | Full team |
| Upstream specs | `02-specify-project-requirements.md` |
| Downstream specs | ERD, API, Android UI, RAG, agent, Docker, test plan |

## Technology Stack Baseline

This table is the cross-component technology stack reference for the project. The
component specs below remain the detailed source of truth for behaviour, API
contracts, schemas, UI flows, AI logic, Docker services, and validation rules.

| Layer | Stack | Repo Evidence | Constraints |
| --- | --- | --- | --- |
| Android mobile client | Kotlin, Android Gradle Plugin `8.13.2`, Kotlin Android plugin `2.0.21`, XML layouts, AppCompat, View Binding, Retrofit/Gson/OkHttp, Material Components, Google Play Services Auth for optional SSO | `android-app/build.gradle`, `android-app/app/build.gradle`, `07-plan-android-ui-flows.md` | Android remains outside Docker and calls only the Spring Boot API. It must not call MySQL or Python AI directly. |
| Java backend | Java `17`, Spring Boot `3.3.5`, Maven, Spring Web, Spring Security, Spring Data JPA, Validation, Actuator, MySQL Connector/J, JJWT `0.12.6`, OAuth2 resource server support for Google token verification | `spring-backend/pom.xml`, `spring-backend/Dockerfile`, `06-plan-api-contracts.md` | Spring Boot is the canonical required backend for `REQ-08`; it owns auth, authorization, business rules, and MySQL writes. |
| Transactional database | MySQL `8.4` in Docker Compose, JPA-owned schema, named volume `mysql-data`; H2 may be used for isolated backend tests | `docker-compose.yml`, `05-plan-backend-data-model-erd.md` | MySQL stores app transactions only. Vector embeddings and curated RAG source files stay outside MySQL. |
| Python AI service | Python `3.12` container, FastAPI `0.115.6`, Uvicorn, Pydantic v2, HTTPX, ChromaDB `0.5.23`, LangChain Core/Ollama, optional LangSmith tracing | `python-ai-service/Dockerfile`, `python-ai-service/requirements.txt`, `08-plan-rag-ai-design.md`, `09-plan-agentic-ai-workflow.md` | Python owns RAG retrieval, Ollama calls, and recommendation analysis. It does not own user auth or direct client-facing business rules. |
| Local AI runtime and vector store | Ollama container, default generation model `qwen2.5:1.5b`, optional slower fallback `llama3.2:3b`, embedding model `nomic-embed-text:latest`, Chroma persistent volume `chroma-data` | `docker-compose.yml`, `08-plan-rag-ai-design.md`, `10-plan-docker-devops.md` | AI must stay free/local by default. Do not introduce paid LLM APIs or cloud-only inference dependencies. |
| Backend/runtime orchestration | Docker Compose for MySQL, Spring Boot, Python AI, Ollama, Chroma persistence, optional Adminer, optional `.NET Backup API`; Android runs on host/emulator/device | `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.dotnet-backup.yml`, `10-plan-docker-devops.md` | Local Compose is the primary demo path. Host-facing ports must be configurable through environment variables. |
| CI, quality, and deployment | GitHub Actions, Maven/Gradle/Pytest/.NET test jobs, Codex Security evidence, optional SonarQube Community Build, optional DigitalOcean deployment with Terraform, Ansible, Caddy, and GHCR images | `.github/workflows/`, `infra/`, `docker-compose.sonar.yml`, `10-plan-docker-devops.md`, `14-validate-quality-gates.md` | CI must avoid heavyweight live LLM generation. Deployment and SonarQube are evidence paths, not replacements for local demo validation. |
| Optional backup backend | `.NET 10.0`, ASP.NET Core, `MySql.Data`, `System.IdentityModel.Tokens.Jwt`, `BCrypt.Net-Next` | `dotnet-backend/Wellness.Backup.Api.csproj`, `06-plan-api-contracts.md` | Cold-standby parity only. It must not replace Spring Boot as the required backend. |
| Optional desktop client | `.NET 10.0`, Avalonia `11.2.1`, CommunityToolkit.Mvvm `8.3.2` | `desktop-app/src/WellnessDesktop/WellnessDesktop.csproj`, `02-specify-project-requirements.md` | Bonus REST client only. It consumes the same Spring Boot contracts and must not replace Android. |

Stack-change rules:

- Changing a framework/runtime family, storage engine, LLM runtime, default model,
  API client approach, or Docker topology requires updating this section and the
  controlling component spec in the same PR.
- Exact patch/minor dependency bumps may be recorded in implementation files
  only when they do not change the architecture, contracts, demo setup, or
  validation evidence.
- No stack change may weaken the CA constraints: Kotlin/XML Android, Java Spring
  Boot backend, MySQL persistence through backend services, local/free AI, and
  Dockerised backend/runtime services where practical.

## Logical Architecture

```plantuml
@startuml
left to right direction

rectangle "Android App\nKotlin + XML Layouts" as Android
rectangle "Desktop App (optional)\n.NET Avalonia (C#)" as Desktop
rectangle "Spring Boot API\nAuth, SSO, Records, Chat,\nRecommendations" as Backend
cloud "Google Identity\nID Token Verification" as Google
rectangle ".NET Backup API\nCold Standby\nSame REST Contracts" as DotNet
database "MySQL\nTransactional Data" as MySQL
rectangle "Python FastAPI AI Service\nRAG + Agentic AI" as AI
database "Chroma Vector Store\nPersisted Embeddings" as Chroma
folder "Curated Wellness Knowledge Base\nMarkdown or JSON" as KB
rectangle "Ollama Local LLM\nqwen2.5:1.5b\nnomic-embed-text" as Ollama

Android --> Backend : HTTPS REST + JWT
Desktop --> Backend : HTTPS REST + JWT\noptional client
Android ..> DotNet : Optional backup base URL\nsame REST + JWT
Android --> Google : Google Sign-In SDK\nID token
Backend --> Google : Verify token claims\nand JWKS
Backend --> MySQL : JPA / JDBC
DotNet --> MySQL : MySQL client\nsame schema
Backend --> AI : HTTP internal API
DotNet --> AI : HTTP internal API\nbackup mode
AI --> Chroma
AI --> KB
AI --> Ollama
AI --> Backend : service token\nprimary internal operations
AI ..> DotNet : service token\nbackup internal operations
@enduml
```

The Java Spring Boot backend remains the primary and canonical backend because `REQ-08` requires Spring Boot. The `.NET Backup API` is optional cold-standby evidence only. It must mirror the Spring Boot wire contracts and MySQL schema, but it must not replace Spring Boot as the required CA backend.

## Responsibility Boundaries

| Component | Responsibilities | Must Not Do |
| --- | --- | --- |
| Android app | UI, form validation, token storage, REST calls to backend | Direct database access, direct Python AI calls |
| Desktop app (optional) | UI, form validation, in-memory token storage, REST calls to backend (parity with Android) | Direct database access, direct Python AI calls, persisting JWT to disk |
| Spring Boot backend | Auth, Google SSO token exchange, JWT, authorization, business rules, MySQL persistence, AI service orchestration | Local embedding/vector logic |
| .NET backup backend | Optional cold-standby REST mirror of Spring contracts for backup rehearsal | Replace Spring as the required backend, change API contracts independently |
| MySQL | Durable transactional data | Store vector embeddings unless specs change |
| Python AI service | RAG indexing/retrieval, Ollama calls, recommendation generation | Own user authentication or bypass backend authorization |
| Chroma | Local vector index persistence | Replace MySQL transactional storage |
| Ollama | Local generation and embeddings | Cloud or paid LLM calls |
| SonarQube Community Build | Optional quality dashboard for maintainability, reliability, duplication, reviewed security issues, and coverage evidence | Replace tests, Codex Security review, SCA, secret scanning, or the local demo path |

## Runtime Architecture

```plantuml
@startuml
top to bottom direction

node "Developer Machine" as Dev {
  rectangle "Android Studio\nEmulator or Device" as AndroidStudio
  node "Docker Compose" as Compose {
    database "mysql container" as MySQL
    rectangle "spring-backend container" as Spring
    rectangle "dotnet-backend container\noptional cold standby" as DotNet
    rectangle "python-ai-service container" as Python
    rectangle "ollama container" as Ollama
    database "mysql-data volume" as MySQLVolume
    database "ollama-data volume" as OllamaVolume
    database "chroma-data volume" as VectorVolume
  }
}

AndroidStudio --> Spring : base URL from dev config
AndroidStudio ..> DotNet : optional backup base URL\nhost port 8082
Spring --> MySQL
DotNet --> MySQL
Spring --> Python
DotNet --> Python
Python --> Ollama
Python --> VectorVolume
MySQL --> MySQLVolume
Ollama --> OllamaVolume
@enduml
```

Local demo default remains `spring-backend` on port `8080`. Backup rehearsal may run `dotnet-backend` on port `8082` and point Android or Python service callbacks to it explicitly. Do not place a gateway or automatic failover in the main demo path unless a later spec revision accepts the added complexity.

## Optional AWS Hybrid Staging

AWS is optional and should not become the only demo path.

```plantuml
@startuml
top to bottom direction

rectangle "GitHub Monorepo" as GitHub
rectangle "GitHub Actions CI" as Actions
rectangle "Build and Test\nAndroid + Spring + Python" as Tests
rectangle "Docker Image Build" as Images
rectangle "Local Demo Stack\nDocker Compose + Ollama" as LocalDemo
cloud "Optional AWS Staging" as AWS
node "EC2 with Docker Compose\nSpring Backend + Python Service" as EC2
database "Amazon RDS MySQL\nor MySQL container" as RDS
rectangle "Final CA Demo Path" as FinalDemo

GitHub --> Actions
Actions --> Tests
Actions --> Images
GitHub --> LocalDemo
GitHub --> AWS
AWS --> EC2
AWS --> RDS
LocalDemo --> FinalDemo
@enduml
```

Recommended AWS usage:

- Use AWS only for shared backend/database staging if the team has time.
- Keep local Docker as the final demo path because the LLM must be free/local.
- Prefer a single EC2 instance running Docker Compose for low setup complexity.
- Use RDS only if the team already has AWS Academy or free-tier confidence.

## DigitalOcean Production Deployment

The chosen production topology: a single DigitalOcean Droplet runs the full Docker
Compose stack with Ollama on-server. Caddy is the only public service and
terminates TLS for `api.<domain>`. Infrastructure is provisioned with Terraform;
images are built by GitHub Actions and the host is configured and deployed with
Ansible (`infra/ansible/`); secrets live in GitHub Actions secrets. See
`10-plan-docker-devops.md` for the operational detail.

```plantuml
@startuml
top to bottom direction

actor "Android App" as Android

cloud "GitHub" as GH {
  rectangle "Actions: infra.yml\nTerraform" as Infra
  rectangle "Actions: deploy.yml\nbuild + Ansible deploy" as Deploy
  rectangle "GHCR\ncontainer images" as GHCR
}

cloud "DigitalOcean" as DO {
  rectangle "Cloud Firewall\ninbound 22/80/443" as FW
  rectangle "Reserved IP + DNS\napi.DOMAIN" as DNS
  storage "Spaces\nTerraform remote state" as Spaces
  node "Droplet 8-16 GB\nUbuntu + Docker Compose" as Droplet {
    rectangle "caddy\nTLS 80/443" as Caddy
    rectangle "spring-backend" as Spring
    rectangle "python-ai-service" as Python
    rectangle "ollama" as Ollama
    database "mysql" as MySQL
  }
}

Infra --> Spaces : state
Infra --> Droplet : provision\n(cloud-init: deploy user)
Infra --> FW
Infra --> DNS
Deploy --> GHCR : push images
Deploy --> Droplet : Ansible: write .env,\ncompose up
Droplet --> GHCR : pull images
Android --> DNS
DNS --> Caddy : HTTPS + JWT
Caddy --> Spring
Spring --> MySQL
Spring --> Python
Python --> Ollama
@enduml
```

Deployment rules:

- Only Caddy (80/443) and SSH (22) are reachable; MySQL, Ollama, Python AI, and
  Spring Boot stay on the internal Docker network.
- No secrets are committed, stored in Terraform state, or placed in cloud-init —
  the Ansible deploy renders `.env` on the Droplet from GitHub Actions secrets
  (read from the environment, never the process argv).
- Ollama remains local to the Droplet, satisfying the free/local LLM constraint
  without any paid cloud inference.

## Quality Dashboard Architecture

SonarQube Community Build is a separate quality-evidence stack, not part of the
wellness app runtime. It supports `REQ-16` by giving reviewers a dashboard for
code quality, maintainability, duplication, reliability issues, reviewed
security findings, and imported coverage where configured. It does not replace
unit tests, Android/manual QA, Codex Security scans, SCA, or secret scanning.

```plantuml
@startuml
top to bottom direction

actor "Team / Reviewer" as Reviewer

cloud "GitHub" as GH {
  rectangle "ci.yml\nbuild + test + scan" as CI
  rectangle "infra.yml\nTerraform app + sonar infra" as Infra
  rectangle "deploy.yml target=sonar\nAnsible deploy" as DeploySonar
}

cloud "DigitalOcean" as DO {
  rectangle "Cloud Firewall\ninbound 22/80/443" as FW
  node "SonarQube Droplet\nUbuntu + Docker Compose" as SonarDroplet {
    rectangle "caddy\nTLS 80/443" as Caddy
    rectangle "sonarqube\nCommunity Build" as Sonar
    database "postgres\nquality metadata" as Postgres
  }
}

rectangle "Code Components\nSpring, Android, Python,\n.NET backup" as Components

CI --> Components : build, test,\ncoverage artifacts
CI --> Sonar : publish analysis\nSONAR_HOST_URL + SONAR_TOKEN
Infra --> SonarDroplet : provision host,\nreserved IP, firewall
Infra --> FW
DeploySonar --> SonarDroplet : Ansible compose up\n.env.sonar from secrets
Reviewer --> Caddy : HTTPS dashboard
Caddy --> Sonar
Sonar --> Postgres
@enduml
```

Quality rules:

- SonarQube runs on a separate local or DigitalOcean stack (`docker-compose.sonar.yml`);
  it is not started by the default wellness app demo command.
- CI scans Spring, Android, Python, and the optional `.NET Backup API` as separate
  projects when `SONAR_HOST_URL` and `SONAR_TOKEN` are configured.
- Spring coverage evidence is imported from JaCoCo XML produced by Maven
  `verify`; Python coverage is imported from `coverage.xml` where configured.
- The SonarQube host exposes only Caddy and SSH publicly; PostgreSQL and
  SonarQube's internal port stay private.

## Main User Flows

### Login, Google SSO, And Wellness CRUD

```plantuml
@startuml
participant User as U
participant "Android App" as A
participant "Spring Boot API" as B
participant "Google Sign-In" as G
database "MySQL" as D

alt Email/password login
U -> A: Enter email and password
A -> B: POST /api/auth/login
B -> D: Verify user password hash
B --> A: JWT access token
else Google SSO login
U -> A: Tap Sign in with Google
A -> G: requestIdToken + requestEmail
G --> A: Google ID token
A -> B: POST /api/auth/google
B -> G: Verify signature, issuer,\naudience, expiry, email
B -> D: Find or create user by email\npassword_hash may be NULL
alt Deactivated Google-only account
  B --> A: 403 reactivation confirmation required
  A --> U: Ask whether to reactivate
  U -> A: Confirm reactivation
  A -> B: POST /api/auth/google\n{idToken, reactivate=true}
  B -> G: Verify token again
  B -> D: Re-enable user after confirmation
end
B --> A: Same internal JWT format
end
U -> A: Create wellness record
A -> B: POST /api/wellness-records with JWT
B -> D: Save record for authenticated user
B --> A: Created record
@enduml
```

Google SSO is an alternate authentication entry point only. After Spring Boot
verifies the Google ID token, Android receives the same internal JWT shape as
email/password login and all subsequent API calls still go through Spring Boot.
SSO-provisioned users have a null `password_hash`; a deactivated Google-only
account can be re-enabled by a fresh verified Google token only after Android
shows a reactivation confirmation dialog, while local-password accounts continue
to use the password-based reactivation path.

### RAG Chatbot With Streaming

```plantuml
@startuml
actor "User" as U
participant "Android App" as A
participant "Spring Boot API" as B
participant "Python AI Service" as P
database "Chroma" as V
participant "Ollama" as O
database "MySQL" as D

U -> A: Send wellness question
A -> B: POST /api/chat/messages/stream\nJWT + question
B -> D: Load recent wellness records\nfor context
B -> P: POST /rag/chat/stream\nquestion + recent records
P -> V: Retrieve relevant wellness KB chunks
P --> B: SSE sources frame
B --> A: SSE sources frame
P -> O: Generate grounded answer\nwith stream=true
loop Token fragments
  O --> P: answer fragment
  P --> B: SSE token frame
  B --> A: SSE token frame
end
B -> D: Save completed question,\nanswer, sources, model
B --> A: SSE done frame\nsaved id + timestamp
@enduml
```

Streaming is the preferred Android path because local Ollama generation can take
tens of seconds on CPU-only machines or small droplets. Spring Boot still owns
authentication, context loading, persistence, and the Android-facing SSE
contract. Python owns retrieval and Ollama streaming. The blocking
`POST /api/chat/messages` -> `POST /rag/chat` path remains as a fallback for
clients that cannot consume Server-Sent Events.

Optional premium weather routing extends this chat path without changing the
Android trust boundary. Android may include coarse last-known latitude/longitude
in the Spring Boot chat request. Spring Boot decides whether the authenticated
user is `PREMIUM_USER`, whether the question is exercise/weather related, and
whether `PREMIUM_AI_URL` is configured. Only then may Spring Boot call the local
`premium-agent` FastAPI service; failures fall back to the standard Python RAG
service. Android must never call the premium agent directly.

### Agentic Recommendation

```plantuml
@startuml
participant "Android App" as A
participant "Spring Boot API" as B
participant "Python AI Service" as P
database "MySQL" as D
database "Chroma" as V
participant "Ollama" as O

A -> B: POST /api/recommendations/generate with JWT
B -> P: POST /agent/recommendation/{userId}
P -> B: GET internal wellness records
B -> D: Load recent records
D --> B: Records
B --> P: Recent records
P -> V: Retrieve supporting wellness guidance
P -> O: Generate personalised recommendation
P -> B: POST internal recommendation
B -> D: Save recommendation
B --> A: Recommendation response
@enduml
```

### Account Privacy, Export, Deactivate, And Delete

```plantuml
@startuml
participant User as U
participant "Android Privacy Screen" as A
participant "Spring Boot API" as B
database "MySQL" as D

U -> A: Open privacy screen from Profile
A --> U: Show local AI + data path statement

alt Export data
  U -> A: Tap Export data
  A -> B: GET /api/account/export\nAuthorization: Bearer JWT
  B -> D: Load current user's profile,\nrecords, chats, recommendations
  D --> B: Owned rows only
  B --> A: JSON export payload
  A --> U: Share/save JSON using Android share sheet
else Deactivate account
  U -> A: Confirm reversible deactivate
  A -> B: POST /api/account/deactivate\nAuthorization: Bearer JWT
  B -> D: Set current user enabled=false\nkeep owned data
  B --> A: 204 No Content
  A -> A: Clear local token
  A --> U: Return to Login
else Delete account
  U -> A: Confirm destructive delete
  alt Local password account
    A -> B: DELETE /api/account\nJWT + password confirmation
    B -> D: Verify password hash
  else Google-only account
    A -> B: DELETE /api/account\nJWT + destructive confirmation only
    B -> D: Confirm password_hash is NULL
  end
  B -> D: Delete owned child rows\nthen user row in one transaction
  B --> A: 204 No Content
  A -> A: Clear local token
  A --> U: Return to Login
end
@enduml
```

Privacy rules:

- Android calls Spring Boot only; it never reads MySQL or calls Python AI directly.
- Export covers MySQL-owned user data only and excludes password hashes, JWTs, service tokens, and infrastructure details.
- Deactivation is reversible: Spring Boot disables the user row, keeps owned records, and existing JWTs stop authenticating because enabled users are required during token filtering.
- Reactivation stays authentication-method aware: local-password accounts use the password-based reactivate endpoint, while Google-only accounts can be restored by a newly verified Google ID token only after Android confirms with the user.
- Permanent delete requires password confirmation for local-password accounts. Google-only accounts have no app password, so a valid JWT plus the Android destructive confirmation is the server-side authorization path.
- Account deletion removes the user's transactional MySQL data. RAG source documents and embeddings are shared local knowledge-base assets, not user uploads, so no per-user vector deletion is needed under the current design.
