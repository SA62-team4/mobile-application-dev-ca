# AI-Enabled Wellness Mobile App

SA62 Mobile Application Development CA project implementing a Kotlin Android wellness app, Spring Boot backend, MySQL persistence, local RAG chatbot, and Python agentic AI workflow.

Start with the specs:

- `docs/specs/00-spec-kit-index.md`
- `docs/specs/01-constitution-principles.md`
- `docs/specs/12-tasks-implementation-backlog.md`
- `docs/specs/14-validate-quality-gates.md`

## Local Runtime

```bash
cp .env.example .env
docker compose up -d mysql ollama
docker compose exec ollama ollama pull llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
docker compose up --build
```

Android remains outside Docker and should point to the Spring Boot backend:

- Emulator: `http://10.0.2.2:8080`
- Physical device: `http://<host-lan-ip>:8080`

## Validation

```bash
plantuml -checkonly docs/specs/*.md
cd spring-backend && mvn test
cd python-ai-service && python3 -m compileall app
```

