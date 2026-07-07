"""FastAPI endpoint tests with service dependencies mocked.

@author zctiong-iss
"""

import importlib
import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import get_settings
from app.models import RagChatResponse, RecommendationResponse


@pytest.fixture
def main_module(tmp_path, monkeypatch):
    """Import app.main with a temp Chroma directory to avoid shared test state."""
    monkeypatch.setenv("CHROMA_PERSIST_DIR", str(tmp_path / "chroma"))
    monkeypatch.setenv("KNOWLEDGE_BASE_DIR", str(tmp_path / "kb"))
    get_settings.cache_clear()

    import app.main as main

    module = importlib.reload(main)
    yield module
    get_settings.cache_clear()


def test_health_endpoint(main_module):
    client = TestClient(main_module.app)

    assert client.get("/health").json() == {"status": "UP"}


def test_reindex_maps_ollama_errors_to_bad_gateway(main_module, monkeypatch):
    class FailingRag:
        async def reindex(self):
            raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(main_module, "rag", FailingRag())
    client = TestClient(main_module.app)

    response = client.post("/rag/reindex")

    assert response.status_code == 502
    assert "Could not reach the AI model service" in response.json()["detail"]


def test_chat_returns_rag_response(main_module, monkeypatch):
    class FakeRag:
        async def chat(self, request):
            return RagChatResponse(
                answer=f"Answer for {request.question}",
                sources=[],
                modelName="qwen2.5:1.5b",
            )

    monkeypatch.setattr(main_module, "rag", FakeRag())
    client = TestClient(main_module.app)

    response = client.post("/rag/chat", json={"userId": 1, "question": "sleep?"})

    assert response.status_code == 200
    assert response.json()["answer"] == "Answer for sleep?"


def test_chat_maps_http_errors_to_bad_gateway(main_module, monkeypatch):
    class FailingRag:
        async def chat(self, request):
            raise httpx.ConnectError("no route")

    monkeypatch.setattr(main_module, "rag", FailingRag())
    client = TestClient(main_module.app)

    response = client.post("/rag/chat", json={"userId": 1, "question": "sleep?"})

    assert response.status_code == 502
    assert "Ollama" in response.json()["detail"]


def test_chat_stream_emits_error_frame_after_http_error(main_module, monkeypatch):
    class FailingStreamRag:
        async def chat_stream(self, request):
            yield "data: {\"type\": \"sources\", \"sources\": []}\n\n"
            raise httpx.ConnectError("stream failed")

    monkeypatch.setattr(main_module, "rag", FailingStreamRag())
    client = TestClient(main_module.app)

    response = client.post("/rag/chat/stream", json={"userId": 1, "question": "sleep?"})
    frames = [
        json.loads(frame.removeprefix("data:").strip())
        for frame in response.text.split("\n\n")
        if frame.strip()
    ]

    assert response.status_code == 200
    assert frames[0] == {"type": "sources", "sources": []}
    assert frames[1]["type"] == "error"
    assert "stream failed" in frames[1]["message"]


def test_recommendation_returns_agent_response(main_module, monkeypatch):
    class FakeAgent:
        async def generate_recommendation(self, user_id: int):
            return RecommendationResponse(
                id=user_id,
                title="Balanced habits",
                trendSummary="Records look steady.",
                recommendationText="Keep your routine.",
                actionItems=["Sleep", "Move", "Log"],
            )

    monkeypatch.setattr(main_module, "agent", FakeAgent())
    client = TestClient(main_module.app)

    response = client.post("/agent/recommendation/5")

    assert response.status_code == 200
    assert response.json()["id"] == 5
    assert response.json()["generatedBy"] == "python-agent"


def test_recommendation_maps_backend_or_ollama_errors_to_bad_gateway(main_module, monkeypatch):
    class FailingAgent:
        async def generate_recommendation(self, user_id: int):
            raise httpx.ConnectError("backend unavailable")

    monkeypatch.setattr(main_module, "agent", FailingAgent())
    client = TestClient(main_module.app)

    response = client.post("/agent/recommendation/5")

    assert response.status_code == 502
    assert "Recommendation agent workflow failed" in response.json()["detail"]
