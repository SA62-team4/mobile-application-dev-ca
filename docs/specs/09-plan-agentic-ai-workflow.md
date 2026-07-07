# 07 Agentic AI Spec

<!-- @author Tiong Zhong Cheng -->

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-13, NFR-01, NFR-03 |
| Primary audience | Python AI owner, backend owner, Android recommendations owner |
| Upstream specs | `05-plan-backend-data-model-erd.md`, `06-plan-api-contracts.md`, `08-plan-rag-ai-design.md` |
| Downstream specs | Agent implementation, recommendation UI, agent tests |

## Goal

Implement a Python-based agentic AI feature that goes beyond direct chatbot replies. The agent should retrieve a user's recent wellness records, analyse trends, make a simple decision, generate a personalised recommendation using local RAG context, save the recommendation, and display it in the mobile app.

## Trigger

Mandatory trigger:

- User taps "Generate recommendation" in the Android Recommendations screen.

Optional trigger:

- Spring Boot scheduled task runs recommendation generation for active users.

## Agent Workflow

```plantuml
@startuml
top to bottom direction

rectangle "Recommendation Requested" as Start
rectangle "Fetch Recent Wellness Records" as Fetch
rectangle "Analyse Sleep, Exercise,\nMood Trends" as Analyze
rectangle "Choose Recommendation Focus" as Decide
rectangle "Retrieve RAG Guidance" as Retrieve
rectangle "Generate Recommendation\nWith Ollama" as Generate
rectangle "Save Recommendation\nThrough Backend" as Save
rectangle "Display In Android App" as Display

Start --> Fetch
Fetch --> Analyze
Analyze --> Decide
Decide --> Retrieve
Retrieve --> Generate
Generate --> Save
Save --> Display
@enduml
```

## LangChain, LangGraph, And LangSmith Stack

The agent is intentionally small and explainable. It uses deterministic Python
rules for the "agentic" decision, LangChain for the local LLM generation chain,
RAG retrieval for grounding, and LangSmith only for optional observability.

| Layer | Current Project Use | Runtime Boundary |
| --- | --- | --- |
| LangChain Core | Builds the generation chain in `AgentService`: `PromptTemplate -> OllamaLLM -> StrOutputParser` | Runs inside `python-ai-service`; calls local Ollama only |
| LangGraph | Workflow model documented as a state graph; not currently installed as a runtime dependency | Future formalisation only; current code follows the same linear graph in service methods |
| LangSmith | Optional tracing for LangChain/RAG/Ollama runs when `LANGSMITH_TRACING=true` and an API key is supplied | Observability only; no inference or business logic |
| Ollama | Local generation model (`qwen2.5:1.5b` by default) | Runs locally through Docker Compose or on the DigitalOcean app Droplet |
| Chroma | Vector retrieval store shared with the RAG chatbot | Persists outside MySQL in the AI service volume |

Current dependency status:

- `langchain-core`, `langchain-ollama`, and `langsmith` are pinned in
  `python-ai-service/requirements.txt`.
- `langgraph` is not currently pinned or imported. If the team adds a concrete
  LangGraph runtime implementation later, update this spec, the task backlog,
  tests, and `requirements.txt` in the same change.

## Agent Runtime Sequence

```plantuml
@startuml
actor "Android User" as User
participant "Android RecommendationsActivity" as Android
participant "Spring Boot API" as Spring
participant "Python AgentService" as Agent
participant "BackendClient" as BackendClient
participant "RagService" as Rag
database "Chroma" as Chroma
participant "LangChain Chain\nPromptTemplate | OllamaLLM | StrOutputParser" as Chain
participant "Ollama\nqwen2.5:1.5b" as Ollama
database "MySQL" as MySQL

User -> Android: Tap Generate recommendation
Android -> Spring: POST /api/recommendations/generate\nJWT
Spring -> Agent: POST /agent/recommendation/{userId}
Agent -> BackendClient: recent_records(userId)
BackendClient -> Spring: GET /api/internal/users/{userId}/wellness-records\nX-Internal-Service-Token
Spring -> MySQL: Load recent records for user
MySQL --> Spring: Recent records
Spring --> BackendClient: Record DTOs
BackendClient --> Agent: records
Agent -> Agent: choose focus + trend summary\n(deterministic rules)
Agent -> Rag: retrieve(focus)
Rag -> Chroma: Top-K wellness guidance chunks
Chroma --> Rag: chunks
Rag --> Agent: source context
Agent -> Chain: ainvoke(focus, trend_summary, context)
Chain -> Ollama: Local generation
Ollama --> Chain: generated text
Chain --> Agent: parsed string
Agent -> Agent: parse title,\nrecommendation, action items
Agent -> BackendClient: save_recommendation(userId, payload)
BackendClient -> Spring: POST /api/internal/users/{userId}/recommendations
Spring -> MySQL: Save recommendation row
MySQL --> Spring: Saved recommendation
Spring --> Agent: RecommendationResponse
Agent --> Spring: RecommendationResponse
Spring --> Android: 201 Created recommendation
@enduml
```

## Detailed Steps

1. Spring Boot receives `POST /api/recommendations/generate` from authenticated Android user.
2. Spring Boot calls Python `POST /agent/recommendation/{userId}`.
3. Python calls Spring Boot internal API to retrieve recent wellness records, default last 14 days.
4. Python analyses trends:
   - Average sleep hours.
   - Number of exercise days.
   - Average exercise minutes.
   - Average mood score.
   - Missing or sparse data.
5. Python chooses one recommendation focus:
   - Sleep consistency.
   - Exercise routine.
   - Mood and stress support.
   - Data consistency when records are sparse.
6. Python retrieves relevant RAG chunks for the chosen focus.
7. Python prompts Ollama to generate a practical recommendation.
   - Keep the RAG context and generated output bounded so CPU-only Ollama
     inference on the DigitalOcean droplet can complete during the demo.
8. Python saves the recommendation through Spring Boot internal API.
9. Spring Boot returns the saved recommendation to Android.

## LangChain Model Flow

The current implementation uses one LangChain expression-language chain for the
generation step only. Data retrieval, trend analysis, focus selection, saving,
and parsing remain ordinary Python service logic so they are easy to unit-test
without a live model.

```plantuml
@startuml
left to right direction

rectangle "Focus\nselected by rules" as Focus
rectangle "Trend Summary\ncomputed from records" as Trend
rectangle "RAG Context\nretrieved KB snippets" as Context
rectangle "PromptTemplate\nwellness-safe instruction" as Prompt
rectangle "OllamaLLM\nqwen2.5:1.5b\ntemperature 0.3" as LLM
rectangle "StrOutputParser\nraw text response" as Parser
rectangle "Post Parser\nTitle + recommendation\n+ 3 action items" as PostParser
rectangle "InternalRecommendationRequest" as Request

Focus --> Prompt
Trend --> Prompt
Context --> Prompt
Prompt --> LLM
LLM --> Parser
Parser --> PostParser
PostParser --> Request
@enduml
```

Prompt contract:

- Input variables: `focus`, `trend_summary`, `context`.
- Output headings requested from the model: `Title:`, `Recommendation:`, and
  `Action items:`.
- Temperature stays low (`0.3`) so the demo output is practical and stable.
- If parsing cannot find three action items, the service supplies safe fallback
  action items rather than saving an incomplete recommendation.

## LangGraph Workflow Model

The current code follows a linear state graph but does not yet run LangGraph.
The graph below is the intended formal model if the team later introduces
`langgraph` as an implementation dependency.

```plantuml
@startuml
hide empty description

[*] --> FetchRecords
FetchRecords --> AnalyzeTrends
AnalyzeTrends --> ChooseFocus
ChooseFocus --> RetrieveRagContext
RetrieveRagContext --> GenerateWithLangChain
GenerateWithLangChain --> ParseModelOutput
ParseModelOutput --> SaveRecommendation
SaveRecommendation --> [*]

FetchRecords : State input: user_id
FetchRecords : State output: records
AnalyzeTrends : averages + sparse-data check
ChooseFocus : deterministic branch
RetrieveRagContext : RagService.retrieve(focus)
GenerateWithLangChain : PromptTemplate | OllamaLLM | StrOutputParser
ParseModelOutput : title, body, 3 action items
SaveRecommendation : Spring internal API write
@enduml
```

State shape for a future LangGraph implementation:

| State Field | Meaning |
| --- | --- |
| `user_id` | Authenticated backend user id passed by Spring |
| `records` | Recent wellness records returned by Spring internal API |
| `focus` | Deterministic recommendation focus |
| `trend_summary` | Human-readable trend summary saved with the recommendation |
| `context_chunks` | RAG snippets retrieved from Chroma |
| `generated_text` | Raw LangChain/Ollama output |
| `recommendation_payload` | Parsed payload sent back to Spring |

LangGraph should remain deterministic for this coursework scope: avoid adding
open-ended tool loops, autonomous external calls, or direct database writes from
Python.

## Decision Rules

Use simple deterministic decision rules before calling the LLM:

- If fewer than 3 wellness records exist, focus on consistent tracking.
- Else if average sleep is below 7 hours, focus on sleep consistency.
- Else if exercise occurs fewer than 3 days in the last 7 days, focus on light activity routine.
- Else if average mood score is 2 or lower, focus on stress and mood support.
- Else focus on maintaining balanced habits.

These rules make the agentic behavior explainable for marking.

## Recommendation Output

The saved recommendation should include:

- Title
- Trend summary
- Recommendation text
- 3 action items
- Generated timestamp
- `generatedBy` value of `python-agent`

## Safety Rules

- Recommendations are wellness habit suggestions, not medical diagnosis.
- Serious symptoms should prompt the user to seek professional medical advice.
- The agent should not claim certainty from sparse data.
- The agent should mention when the user needs more logs for better personalisation.

## Failure Modes

| Failure | Expected Behavior |
| --- | --- |
| No wellness records | Generate a tracking-focused starter recommendation |
| Backend internal API unavailable | Return controlled error to Spring Boot |
| Ollama unavailable | Return controlled AI unavailable error |
| Save fails | Return error and do not pretend recommendation was saved |
| Timeout | Android shows friendly retry message |

## Observability (LangSmith Tracing)

The recommendation workflow is built with LangChain (a
`PromptTemplate | OllamaLLM | StrOutputParser` chain), so its generation step is
traced to LangSmith automatically when tracing is enabled. The shared
`rag.retrieve` step (see `08-plan-rag-ai-design.md`) is instrumented with
`@traceable`, so a generated recommendation produces a single run tree covering
retrieval and generation.

- Tracing is an observability layer only and does not perform inference, so the
  local/free AI constraint holds: with tracing disabled (the default) the agent
  runs fully local/offline on Ollama.
- Configuration is env-driven and off by default (`LANGSMITH_TRACING`,
  `LANGSMITH_API_KEY`, `LANGSMITH_PROJECT`, `LANGSMITH_ENDPOINT`); startup
  no-ops with a warning if enabled without an API key.
- The deterministic decision rules run before the LLM and are not affected by
  tracing, so the workflow stays explainable for marking.
- Deploy wiring for these variables is defined in `10-plan-docker-devops.md`.

```plantuml
@startuml
top to bottom direction

rectangle "AgentService.generate_recommendation" as AgentRun
rectangle "BackendClient.recent_records" as Records
rectangle "Deterministic rules\nchoose focus" as Rules
rectangle "rag.retrieve\n@traceable retriever" as Retrieve
rectangle "LangChain generation chain" as Chain
rectangle "OllamaLLM run" as LLM
rectangle "BackendClient.save_recommendation" as Save
cloud "LangSmith Project\nwellness-agentic-ai" as LangSmith

AgentRun --> Records
AgentRun --> Rules
AgentRun --> Retrieve
AgentRun --> Chain
Chain --> LLM
AgentRun --> Save
Retrieve ..> LangSmith : trace when enabled
Chain ..> LangSmith : LangChain run tree\nwhen enabled
LLM ..> LangSmith : local model call metadata\nwhen enabled
@enduml
```

Trace evidence expected during demo preparation:

- With tracing disabled, recommendation generation must still work fully
  offline/local.
- With tracing enabled and `LANGSMITH_API_KEY` configured, the run tree should
  show retrieval plus the LangChain generation call; it must not include secrets,
  JWTs, internal service tokens, or full service objects.
- Trace names should stay readable and stable enough for screenshots:
  `rag.retrieve`, LangChain generation, and Ollama LLM calls.

## Acceptance Criteria

- Agent retrieves recent records instead of relying only on the user's prompt.
- Agent analyses trends before generation.
- Agent chooses a recommendation focus using deterministic rules.
- Agent uses RAG context and Ollama locally.
- Recommendation is saved in MySQL through Spring Boot.
- Android can display generated recommendations.
- LangChain generation uses local Ollama through `langchain-ollama`; no paid or
  cloud-only LLM dependency is introduced.
- LangGraph is either documented-only, as above, or implemented with an explicit
  dependency/spec/test update.
- Tracing is disabled by default; when enabled, a generated recommendation
  appears as a LangSmith run tree without changing the saved output.
