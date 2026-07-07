# Local AI Services

The repository uses local AI in two layers: a RAG chatbot and an agentic recommendation workflow.

## RAG chatbot

The RAG design is documented in `docs/specs/08-plan-rag-ai-design.md`.

Key traits:

- uses a curated wellness knowledge base in `rag-knowledge-base/`
- generates embeddings locally through Ollama
- persists the vector index outside MySQL
- retrieves a small number of relevant chunks before generation
- returns source summaries alongside answers

The design explicitly avoids paid LLM APIs and cloud-only model dependencies.

## Agentic recommendations

The agentic workflow is documented in `docs/specs/09-plan-agentic-ai-workflow.md`.

It follows a deterministic sequence:

1. Spring Boot receives a recommendation request.
2. Python fetches recent wellness records through the backend.
3. Python analyses trends such as sleep, exercise, and mood.
4. The agent chooses a recommendation focus using simple decision rules.
5. Python retrieves relevant RAG context.
6. Ollama generates a recommendation.
7. The result is saved through the backend and returned to Android.

## Why the split exists

The split between Spring Boot and Python is intentional:

- Spring Boot owns business rules and persistence.
- Python owns retrieval, inference orchestration, and generation prompts.
- The backend mediates every app-visible write.

This keeps the main data model in one place and lets the AI service remain an isolated, local dependency.

## Operational constraints

The AI stack is designed to work locally during development and demo runs. The docs/specs assume:

- Ollama for generation
- `qwen2.5:1.5b` for generation
- `nomic-embed-text` for embeddings
- bounded prompt/output sizes so CPU-only inference stays usable

## Change guidance

When changing AI code or behavior:

1. Update the RAG or agent spec first.
2. Check whether the change affects stored chat/recommendation data.
3. Keep Android out of the AI service path; it should still go through Spring Boot.
4. Validate with the relevant AI or backend tests and a local demo path.

## Best starting points

- `docs/specs/08-plan-rag-ai-design.md`
- `docs/specs/09-plan-agentic-ai-workflow.md`
- `rag-knowledge-base/`
- `python-ai-service/`
