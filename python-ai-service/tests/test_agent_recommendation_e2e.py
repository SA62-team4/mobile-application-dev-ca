"""End-to-end test for the agentic recommendation workflow (T-503, REQ-13).

Ticket acceptance: "the agent generates AND saves a recommendation through the
backend internal API" and "an end-to-end generate call writes a recommendation
row and returns it".

Unlike ``test_agent_service.py`` (which fakes the backend) and
``test_backend_client.py`` (which tests the client in isolation), this test
wires the **real** :class:`AgentService` to the **real** :class:`BackendClient`
and drives them through an in-memory stand-in for the Spring internal API. Only
the two genuinely external, non-deterministic seams are faked:

* the Ollama generation chain (``service.chain``), and
* RAG retrieval (Chroma embeddings).

Everything else runs real code: trend analysis, deterministic focus selection,
output parsing, HTTP request construction, the internal-token header, and the
save round-trip. That is what makes this an end-to-end proof that "generate"
actually writes a row through Spring and hands the saved row back.

The stub also lets us assert the ticket's "known footgun": the client must call
the **Spring** backend, never the ``dotnet-backend`` backup (whose absence
surfaces as a "Name or service not known" DNS failure on save).

Live demo equivalent (run once services are up, not exercised by CI):

    # Spring, MySQL, Ollama and the python-ai-service running via docker-compose
    curl -X POST http://localhost:8000/agent/recommendation/1
    # -> 200 with a RecommendationResponse whose id is populated, and a new
    #    row visible via GET http://localhost:8080/api/recommendations (JWT).

@author Chua Wei Yi Justin
"""

import json

import httpx
import pytest

from app.agent_service import AgentService
from app.backend_client import BackendClient
from app.config import Settings
from app.knowledge_base import KnowledgeChunk

# The correct target host. The .NET backup host is the documented footgun.
SPRING_HOST = "spring-backend"
DOTNET_BACKUP_HOST = "dotnet-backend"
SPRING_BASE_URL = f"http://{SPRING_HOST}:8080"
INTERNAL_TOKEN = "internal-token-e2e"


class StubSpring:
    """In-memory stand-in for the Spring internal API.

    Models the two endpoints the agent depends on and keeps a ``rows`` list that
    behaves like the ``recommendations`` table so the test can assert a row was
    actually written. Every request is captured for URL/token assertions.
    """

    def __init__(self, records: list[dict], fail_save: bool = False):
        self._records = records
        self._fail_save = fail_save
        self.rows: list[dict] = []
        self.requests: list[httpx.Request] = []
        self._next_id = 1

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        path = request.url.path
        token = request.headers.get("X-Internal-Service-Token")

        # Mirror Spring: a bad internal token is rejected, never silently served.
        if token != INTERNAL_TOKEN:
            return httpx.Response(403, json={"message": "Invalid internal service token"})

        if request.method == "GET" and path.endswith("/wellness-records"):
            return httpx.Response(200, json=self._records)

        if request.method == "POST" and path.endswith("/recommendations"):
            if self._fail_save:
                return httpx.Response(503, json={"message": "backend down"})
            payload = json.loads(request.content)
            row = {
                "id": self._next_id,
                "title": payload["title"],
                "trendSummary": payload["trendSummary"],
                "recommendationText": payload["recommendationText"],
                "actionItems": payload["actionItems"],
                "generatedBy": payload.get("generatedBy", "python-agent"),
                "createdAt": "2026-07-08T09:00:00Z",
            }
            self._next_id += 1
            self.rows.append(row)  # "persist" the recommendation row.
            return httpx.Response(201, json=row)

        return httpx.Response(404, json={"message": f"Unhandled {request.method} {path}"})


class FakeRag:
    """Deterministic RAG stand-in so no Chroma/embedding model is needed."""

    def __init__(self):
        self.last_question: str | None = None

    async def retrieve(self, question: str, top_k: int = 3):
        self.last_question = question
        return [
            KnowledgeChunk(
                id="sleep-0",
                title="Sleep Hygiene",
                source_file="sleep-hygiene.md",
                chunk_index=0,
                text="Keep a regular bedtime and wind down before sleep.",
                snippet="Keep a regular bedtime and wind down before sleep.",
            )
        ]


class FakeChain:
    """Deterministic replacement for the LangChain | Ollama generation chain."""

    GENERATED = (
        "Title: Steady Sleep Reset\n"
        "Recommendation: Wind down at a consistent time each night.\n"
        "Action items:\n"
        "- Set a fixed bedtime\n"
        "- Dim screens an hour before bed\n"
        "- Log your sleep tomorrow"
    )

    def __init__(self):
        self.input_payload: dict | None = None

    async def ainvoke(self, payload: dict) -> str:
        self.input_payload = payload
        return self.GENERATED


def _build(monkeypatch, records: list[dict], base_url: str = SPRING_BASE_URL,
           fail_save: bool = False):
    """Wire a real AgentService + real BackendClient against a StubSpring.

    Returns ``(service, stub)``. The BackendClient's ``httpx.AsyncClient`` is
    replaced by a factory that injects a MockTransport so the *real* client code
    path (URL building, headers, raise_for_status, response parsing) executes.
    """
    from tests.conftest import FakeOllama

    stub = StubSpring(records, fail_save=fail_save)
    transport = httpx.MockTransport(stub.handler)
    real_async_client = httpx.AsyncClient

    def factory(**kwargs):
        return real_async_client(transport=transport, **kwargs)

    monkeypatch.setattr("app.backend_client.httpx.AsyncClient", factory)

    settings = Settings(
        BACKEND_BASE_URL=base_url,
        INTERNAL_SERVICE_TOKEN=INTERNAL_TOKEN,
        CHROMA_PERSIST_DIR="./unused-e2e-chroma",
        KNOWLEDGE_BASE_DIR="../rag-knowledge-base",
    )
    ollama = FakeOllama()
    ollama.settings = settings

    service = AgentService(BackendClient(settings), FakeRag(), ollama)
    service.chain = FakeChain()
    return service, stub


# Records with average sleep below 7h -> deterministic focus "sleep consistency".
_LOW_SLEEP_RECORDS = [
    {"recordDate": "2026-07-05", "sleepHours": 6.0, "exerciseMinutes": 30, "moodScore": 4},
    {"recordDate": "2026-07-06", "sleepHours": 6.5, "exerciseMinutes": 20, "moodScore": 4},
    {"recordDate": "2026-07-07", "sleepHours": 6.8, "exerciseMinutes": 15, "moodScore": 4},
]


async def test_generate_call_writes_a_recommendation_row_and_returns_it(monkeypatch):
    service, stub = _build(monkeypatch, _LOW_SLEEP_RECORDS)

    response = await service.generate_recommendation(user_id=7)

    # 1. A row was written through the Spring internal API ("saves").
    assert len(stub.rows) == 1
    row = stub.rows[0]
    assert row["generatedBy"] == "python-agent"
    assert row["title"] == "Steady Sleep Reset"
    assert row["actionItems"] == [
        "Set a fixed bedtime",
        "Dim screens an hour before bed",
        "Log your sleep tomorrow",
    ]

    # 2. The saved row is returned to the caller with a populated id ("returns it").
    assert response.id == 1
    assert response.title == "Steady Sleep Reset"
    assert response.createdAt == "2026-07-08T09:00:00Z"
    assert response.recommendationText  # non-empty body persisted

    # 3. The deterministic agent behaviour drove the pipeline before the LLM ran.
    assert service.rag.last_question == "sleep consistency"
    assert service.chain.input_payload["focus"] == "sleep consistency"
    assert "Sleep Hygiene" in service.chain.input_payload["context"]
    assert "average sleep is 6.4 hours" in response.trendSummary


async def test_records_are_fetched_then_recommendation_saved_via_spring_paths(monkeypatch):
    service, stub = _build(monkeypatch, _LOW_SLEEP_RECORDS)

    await service.generate_recommendation(user_id=7)

    methods_paths = [(r.method, r.url.path) for r in stub.requests]
    assert methods_paths == [
        ("GET", "/api/internal/users/7/wellness-records"),
        ("POST", "/api/internal/users/7/recommendations"),
    ]
    # Both internal calls carry the service token and the GET honours the 14d window.
    get_request = stub.requests[0]
    assert get_request.headers["X-Internal-Service-Token"] == INTERNAL_TOKEN
    assert get_request.url.params["days"] == "14"
    assert stub.requests[1].headers["X-Internal-Service-Token"] == INTERNAL_TOKEN


async def test_backend_client_targets_spring_not_dotnet_backup(monkeypatch):
    """Regression guard for the documented footgun.

    Every internal call must go to the configured Spring host, never the
    ``dotnet-backend`` backup that 503s recommendations with
    'Name or service not known'.
    """
    service, stub = _build(monkeypatch, _LOW_SLEEP_RECORDS)

    await service.generate_recommendation(user_id=7)

    assert stub.requests, "expected the agent to call the backend"
    for request in stub.requests:
        assert request.url.host == SPRING_HOST
        assert request.url.host != DOTNET_BACKUP_HOST


async def test_client_target_is_config_driven_so_misconfig_would_hit_dotnet(monkeypatch):
    """The Spring vs .NET choice is governed purely by BACKEND_BASE_URL.

    Proving the target follows config (rather than being hardcoded to Spring)
    is what makes the footgun a *deployment* concern the startup log guards, and
    shows exactly how a bad override would have routed to the backup host.
    """
    service, stub = _build(
        monkeypatch,
        _LOW_SLEEP_RECORDS,
        base_url=f"http://{DOTNET_BACKUP_HOST}:8080",
    )

    await service.generate_recommendation(user_id=7)

    assert all(r.url.host == DOTNET_BACKUP_HOST for r in stub.requests)


async def test_save_failure_is_not_swallowed(monkeypatch):
    """If Spring rejects the save, the error propagates (no fake success)."""
    service, stub = _build(monkeypatch, _LOW_SLEEP_RECORDS, fail_save=True)

    with pytest.raises(httpx.HTTPStatusError):
        await service.generate_recommendation(user_id=7)

    assert stub.rows == []  # nothing persisted on failure
    # The failure happened at the POST save step, after records were fetched.
    assert [r.method for r in stub.requests] == ["GET", "POST"]
