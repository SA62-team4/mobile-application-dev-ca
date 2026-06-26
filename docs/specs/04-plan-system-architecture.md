# 02 System Architecture

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-08, REQ-09, REQ-10, REQ-13, REQ-14, NFR-03 |
| Primary audience | Full team |
| Upstream specs | `02-specify-project-requirements.md` |
| Downstream specs | ERD, API, Android UI, RAG, agent, Docker, test plan |

## Logical Architecture

```plantuml
@startuml
left to right direction

rectangle "Android App\nKotlin + XML Layouts" as Android
rectangle "Spring Boot API\nAuth, Records, Chat,\nRecommendations" as Backend
database "MySQL\nTransactional Data" as MySQL
rectangle "Python FastAPI AI Service\nRAG + Agentic AI" as AI
database "Chroma Vector Store\nPersisted Embeddings" as Chroma
folder "Curated Wellness Knowledge Base\nMarkdown or JSON" as KB
rectangle "Ollama Local LLM\nllama3.2:3b\nnomic-embed-text" as Ollama

Android --> Backend : HTTPS REST + JWT
Backend --> MySQL : JPA / JDBC
Backend --> AI : HTTP internal API
AI --> Chroma
AI --> KB
AI --> Ollama
AI --> Backend : service token\ninternal operations
@enduml
```

## Responsibility Boundaries

| Component | Responsibilities | Must Not Do |
| --- | --- | --- |
| Android app | UI, form validation, token storage, REST calls to backend | Direct database access, direct Python AI calls |
| Spring Boot backend | Auth, JWT, authorization, business rules, MySQL persistence, AI service orchestration | Local embedding/vector logic |
| MySQL | Durable transactional data | Store vector embeddings unless specs change |
| Python AI service | RAG indexing/retrieval, Ollama calls, recommendation generation | Own user authentication or bypass backend authorization |
| Chroma | Local vector index persistence | Replace MySQL transactional storage |
| Ollama | Local generation and embeddings | Cloud or paid LLM calls |

## Runtime Architecture

```plantuml
@startuml
top to bottom direction

node "Developer Machine" as Dev {
  rectangle "Android Studio\nEmulator or Device" as AndroidStudio
  node "Docker Compose" as Compose {
    database "mysql container" as MySQL
    rectangle "spring-backend container" as Spring
    rectangle "python-ai-service container" as Python
    rectangle "ollama container" as Ollama
    database "mysql-data volume" as MySQLVolume
    database "ollama-data volume" as OllamaVolume
    database "chroma-data volume" as VectorVolume
  }
}

AndroidStudio --> Spring : base URL from dev config
Spring --> MySQL
Spring --> Python
Python --> Ollama
Python --> VectorVolume
MySQL --> MySQLVolume
Ollama --> OllamaVolume
@enduml
```

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

## Main User Flows

### Login And Wellness CRUD

```plantuml
@startuml
participant User as U
participant "Android App" as A
participant "Spring Boot API" as B
database "MySQL" as D

U -> A: Enter email and password
A -> B: POST /api/auth/login
B -> D: Verify user password hash
B --> A: JWT access token
U -> A: Create wellness record
A -> B: POST /api/wellness-records with JWT
B -> D: Save record for authenticated user
B --> A: Created record
@enduml
```

### RAG Chatbot

```plantuml
@startuml
participant "Android App" as A
participant "Spring Boot API" as B
participant "Python AI Service" as P
database "Chroma" as V
participant "Ollama" as O
database "MySQL" as D

A -> B: POST /api/chat/messages with question and JWT
B -> P: POST /rag/chat with user context
P -> V: Retrieve relevant wellness KB chunks
P -> O: Generate grounded answer with context
O --> P: Answer
P --> B: Answer and source snippets
B -> D: Save chat message
B --> A: Chat response
@enduml
```

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
