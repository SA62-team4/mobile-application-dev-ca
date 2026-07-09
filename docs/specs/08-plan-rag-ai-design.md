# 06 RAG AI Spec

<!-- @author Tiong Zhong Cheng -->

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-10, REQ-11, REQ-12, NFR-03, NFR-05 |
| Primary audience | Python AI owner, backend owner, test owner |
| Upstream specs | `04-plan-system-architecture.md`, `06-plan-api-contracts.md` |
| Downstream specs | Python RAG service, chatbot integration tests |

## Goal

Implement a basic full RAG system for wellness chatbot responses using only local/free tools. The chatbot should retrieve relevant wellness guidance from a curated knowledge base, then ask a local Ollama model to answer using that context.

## Non-Negotiable AI Constraint

No paid LLM APIs and no cloud-only model dependency.

Default local models:

- Generation: `qwen2.5:1.5b` (same latency class as `llama3.2:1b` on CPU-only but better
  instruction-following; override `OLLAMA_GENERATION_MODEL` with `llama3.2:3b` when answer
  quality matters more than latency, noting the ~2-3x latency and higher memory cost)
- Embeddings: `nomic-embed-text`

Default local runtime:

- Ollama

## Knowledge Base

Use a curated wellness knowledge base created by the team during implementation.

Planned location:

```text
rag-knowledge-base/
```

Suggested documents:

- `sleep-hygiene.md`
- `exercise-basics.md`
- `stress-and-mood.md`
- `hydration-and-nutrition.md`
- `healthy-habits-faq.md`
- `bmi-and-body-metrics.md`

Each document should include:

- Title
- Short source note such as "team-curated educational summary"
- Content written in simple, non-clinical language
- Disclaimer that the app does not provide medical diagnosis

The curated files should stay retrieval-friendly for the CPU-bound demo path:

- Keep each topic concise enough to produce one or a small number of focused chunks.
- Put common user query terms in the topic body, for example "BMI", "body mass
  index", "height", and "weight" in the body metrics document.
- Treat BMI as a screening and habit-support topic only. The chatbot may explain
  the standard adult BMI formula and broad screening categories, but must state
  that BMI is not a diagnosis and should be interpreted with other health factors.

## RAG Pipeline

```plantuml
@startuml
left to right direction

folder "Curated KB Files" as KB
rectangle "Chunk Text" as Chunker
rectangle "Ollama Embeddings\nnomic-embed-text" as Embed
database "Chroma Vector Store" as Store
rectangle "User Question" as UserQuestion
rectangle "Query Embedding" as QueryEmbed
rectangle "Top K Retrieval" as Retrieve
rectangle "Grounded Prompt" as Prompt
rectangle "Ollama Generation\nqwen2.5:1.5b" as Generate
rectangle "Answer + Sources" as Answer

KB --> Chunker
Chunker --> Embed
Embed --> Store
UserQuestion --> QueryEmbed
QueryEmbed --> Retrieve
Store --> Retrieve
Retrieve --> Prompt
Prompt --> Generate
Generate --> Answer
@enduml
```

## Indexing Behavior

- Load Markdown or JSON knowledge files.
- Split content into small chunks of roughly 300 to 600 words.
- Store metadata for each chunk:
  - `title`
  - `source_file`
  - `chunk_index`
  - `snippet`
- Snippets used in prompts and source summaries should skip repeated Markdown
  boilerplate such as title lines, source notes, and the general medical
  disclaimer so the limited prompt context carries actionable wellness guidance.
- Generate embeddings locally through Ollama.
- Use Ollama's current `POST /api/embed` endpoint with `nomic-embed-text:latest`
  for embeddings, with compatibility fallback to the older
  `POST /api/embeddings` endpoint when the runtime does not expose `/api/embed`.
- Persist the Chroma index in a Docker volume.
- Provide a development-only reindex endpoint.

```plantuml
@startuml
title Reindex Flow

participant "Reindex Endpoint" as API
participant "RagService" as RAG
participant "KB Loader" as KB
participant "Ollama\n(nomic-embed-text)" as Ollama
database "Chroma\nwellness_kb" as Chroma

API -> RAG: reindex()
RAG -> KB: load_chunks(knowledge_base_dir)
KB --> RAG: chunks

alt no chunks found
  RAG --> API: 400 No knowledge base files found
end

RAG -> Chroma: get() existing ids
Chroma --> RAG: ids
RAG -> Chroma: delete(ids)

loop for each chunk
  RAG -> Ollama: embed(chunk.text)
  Ollama --> RAG: embedding
  RAG -> Chroma: add(id, embedding, document, metadata)
end

RAG --> API: {"chunks": N}
@enduml
```

## Retrieval Behavior

- Embed the user question.
- Retrieve top 3 to 5 relevant chunks.
- Include recent wellness record context when Spring Boot provides it.
- If retrieval confidence is low, still answer cautiously and state that the available knowledge base has limited context.

## Prompt Rules

The generation prompt must instruct the model to:

- Answer only wellness education and habit-support questions.
- Use retrieved context first.
- Consider recent wellness records if provided.
- Avoid diagnosis, treatment claims, or emergency advice.
- Encourage professional medical help for serious or urgent symptoms.
- Keep answers concise and practical.
- Return source titles or snippets used.
- Bound local generation length so CPU-only Ollama inference remains usable for
  the Android demo path.

## Chat Response Contract

Python returns:

- `answer`
- `sources`
- `modelName`

Spring Boot saves:

- User question
- Assistant answer
- Source summary
- Model name
- Timestamp

```plantuml
@startuml
title Blocking Chat Flow (POST /rag/chat)

actor Android
participant "Spring Boot" as Backend
participant "RagService" as RAG
database "Chroma" as Chroma
participant "Ollama" as Ollama

Android -> Backend: chat request (+ recent records)
Backend -> RAG: chat(request)

RAG -> RAG: retrieve(question)
RAG -> Chroma: count()
alt index empty
  RAG -> RAG: reindex()
end
RAG -> Ollama: embed(question)
Ollama --> RAG: query embedding
RAG -> Chroma: query(embedding, top_k=3)
Chroma --> RAG: top chunks

RAG -> RAG: build grounded prompt
RAG -> Ollama: generate(prompt, num_predict=220)
Ollama --> RAG: answer

RAG --> Backend: answer + sources + modelName
Backend -> Backend: persist question, answer,\nsources, model, timestamp
Backend --> Android: answer + sources
@enduml
```

### Streaming Variant

A streaming chat path (`POST /rag/chat/stream` → `POST /api/chat/messages/stream`) exists
alongside the blocking one so long answers are not truncated and the user sees tokens as
they are generated. Retrieval runs first (sources are known up front), then Ollama runs with
`stream=True` and each fragment is forwarded as a Server-Sent Events frame. Spring Boot
accumulates the fragments, persists the same fields listed above once the stream completes,
and only then emits the terminal `done` frame — so streamed and non-streamed exchanges are
stored identically. See `06-plan-api-contracts.md` for the SSE frame protocol.

Spring Boot may route eligible premium outdoor-exercise/weather chat requests to
an optional local premium weather agent before using this standard RAG stream.
That route is still backend-mediated, uses the same persisted chat response
shape, and must fall back to the standard RAG flow when the premium agent is not
configured or fails.

```plantuml
@startuml
title Streaming Chat Flow (POST /rag/chat/stream)

actor Android
participant "Spring Boot" as Backend
participant "RagService" as RAG
database "Chroma" as Chroma
participant "Ollama" as Ollama

Android -> Backend: stream chat request
Backend -> RAG: chat_stream(request)

RAG -> Chroma: retrieve top chunks
Chroma --> RAG: chunks
RAG --> Backend: SSE {type: sources}
Backend --> Android: sources frame

RAG -> Ollama: generate_stream(prompt, num_predict=220)
loop each fragment
  Ollama --> RAG: token fragment
  RAG --> Backend: SSE {type: token}
  Backend -> Backend: accumulate fragment
  Backend --> Android: token frame
end

note over RAG: if no tokens produced,\nemit fallback token
RAG --> Backend: SSE {type: done, modelName}
Backend -> Backend: persist accumulated answer\n(same fields as blocking)
Backend --> Android: done frame
@enduml
```

### CPU Performance Tuning

Generation runs CPU-only on the droplet, so latency is managed rather than eliminated:

- **Model**: `qwen2.5:1.5b` by default (~2-3x faster than 3b on CPU, similar speed to
  llama3.2:1b with better output quality).
- **Keep-warm**: the prod overlay sets `OLLAMA_KEEP_ALIVE=-1` and `OLLAMA_NUM_PARALLEL=1`, and
  the deploy warms the generation model, so the first request pays no cold model load and a
  single request uses every core. See `10-plan-docker-devops.md`.
- **Small prompt / context**: retrieval uses top-3 chunks with capped snippet length, and
  generation caps `num_ctx` at 1024, keeping CPU prefill cheap.
- **Streaming**: tokens are streamed to Android so a long answer is perceived as fast even
  when total generation time is unchanged.

## Failure Modes

| Failure | Expected Behavior |
| --- | --- |
| Ollama unavailable | Python returns a clear service-unavailable error; Android shows friendly retry message |
| Vector index missing | Python returns setup/reindex-needed error or triggers safe rebuild during development |
| No relevant chunks | Python gives a cautious general response and source list is empty |
| Question outside wellness scope | Python responds that the chatbot only supports wellness habit questions |
| Timeout | Backend returns controlled timeout error to Android |

## Observability (LangSmith Tracing)

The RAG chat path is instrumented for optional tracing so runs can be inspected
during development and the demo. This is an observability layer only — it does
not perform inference — so the non-negotiable local/free AI constraint is
preserved: with tracing disabled (the default) the service runs fully
local/offline, and Ollama remains the only LLM runtime.

- Instrumented steps use LangSmith's `@traceable` decorator:
  - `rag.chat` (chain), `rag.retrieve` (retriever)
  - `ollama.embed` (embedding) and `ollama.generate` (llm)
  - The streaming path is traced too: `rag.chat.stream` (chain) and
    `ollama.generate.stream` (llm). Streamed fragments are reduced back into a single
    answer string so the run renders like the blocking path.
- The bound service instance is stripped from traced inputs so runs stay
  readable and never serialise Chroma/HTTP clients.
- Configuration is env-driven and off by default:
  - `LANGSMITH_TRACING` (default `false`), `LANGSMITH_API_KEY`,
    `LANGSMITH_PROJECT` (default `wellness-agentic-ai`), `LANGSMITH_ENDPOINT`.
  - Startup no-ops (with a warning) if tracing is enabled without an API key.
- Deploy wiring for these variables is defined in `10-plan-docker-devops.md`.

## RAG Acceptance Criteria

- RAG index can be built from curated KB files.
- Chatbot retrieves relevant chunks before generation.
- Responses include source titles or snippets.
- Ollama is the only LLM runtime used.
- Chat works through Spring Boot, not directly from Android to Python.
- Tracing is disabled by default; when enabled, RAG chat, retrieval, and Ollama
  calls appear as a LangSmith run tree without changing chat behavior.
