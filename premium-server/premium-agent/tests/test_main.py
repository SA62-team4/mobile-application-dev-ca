"""Tests for the premium agent FastAPI routes.

@author Tang Chee Seng (with Claude)
"""

import importlib
import json
import pytest
from fastapi.testclient import TestClient


def _parse_sse(raw: str):
    """Parse an SSE body into a list of decoded JSON frames (one per 'data:' line)."""
    frames = []
    for line in raw.splitlines():
        line = line.strip()
        if line.startswith("data:"):
            payload = line[len("data:"):].strip()
            if payload:
                frames.append(json.loads(payload))
    return frames


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SECRET", "test-secret")
    import app.main as main
    module = importlib.reload(main)   # re-read SECRET from env

    async def fake_run_agent(question, context, records, lat=None, lon=None):
        return {"answer": "It is safe. WBGT 28°C, GREEN zone.", "modelName": "gemma4:e4b"}

    monkeypatch.setattr(module, "run_agent", fake_run_agent)
    return TestClient(module.app)


def test_health_ok(client):
    assert client.get("/health").json() == {"status": "UP"}

def test_premium_chat_rejects_missing_secret(client):
    r = client.post("/premium/chat", json={"question": "safe to run?"})
    assert r.status_code == 422 or r.status_code == 401   # Header(...) required → 422; wrong value → 401

def test_premium_chat_rejects_wrong_secret(client):
    r = client.post("/premium/chat", json={"question": "safe to run?"},
                    headers={"X-Internal-Secret": "wrong"})
    assert r.status_code == 401

def test_premium_chat_happy_path(client):
    r = client.post("/premium/chat",
                    json={"question": "Is it safe to run outside?", "latitude": 1.3, "longitude": 103.8},
                    headers={"X-Internal-Secret": "test-secret"})
    assert r.status_code == 200
    body = r.json()
    assert body["modelName"] == "gemma4:e4b"
    assert "GREEN" in body["answer"]

def test_rate_limit_after_10_per_minute(client):
    headers = {"X-Internal-Secret": "test-secret"}
    payload = {"question": "safe to run?"}
    codes = [client.post("/premium/chat", json=payload, headers=headers).status_code
             for _ in range(12)]
    assert codes.count(429) >= 1   # 11th/12th within the minute should be limited


# --- streaming route (/premium/chat/stream) ---

def test_stream_emits_sources_tokens_done(client):
    r = client.post("/premium/chat/stream",
                    json={"question": "safe to run?", "latitude": 1.3, "longitude": 103.8},
                    headers={"X-Internal-Secret": "test-secret"})
    assert r.status_code == 200
    assert "text/event-stream" in r.headers["content-type"]
    frames = _parse_sse(r.text)
    types = [f["type"] for f in frames]
    assert types[0] == "sources"          # sources frame first
    assert "token" in types               # at least one token frame
    assert types[-1] == "done"            # done frame last
    assert frames[-1]["modelName"] == "gemma4:e4b"

def test_stream_tokens_reconstruct_answer_exactly(client):
    """Regression guard: the word-chunked replay must round-trip losslessly —
    ''.join(token.text) == the original answer. Protects the re.findall pattern
    that previously dropped leading whitespace."""
    r = client.post("/premium/chat/stream",
                    json={"question": "safe to run?"},
                    headers={"X-Internal-Secret": "test-secret"})
    frames = _parse_sse(r.text)
    rebuilt = "".join(f["text"] for f in frames if f["type"] == "token")
    assert rebuilt == "It is safe. WBGT 28°C, GREEN zone."   # the fixture's answer, exactly

def test_stream_rejects_wrong_secret(client):
    r = client.post("/premium/chat/stream", json={"question": "safe to run?"},
                    headers={"X-Internal-Secret": "wrong"})
    assert r.status_code == 401

def test_premium_chat_agent_failure_returns_502(client, monkeypatch):
    """Blocking path: if run_agent raises, the route returns 502, not a 500 stack trace."""
    import app.main as main

    async def boom(*args, **kwargs):
        raise RuntimeError("agent exploded")

    monkeypatch.setattr(main, "run_agent", boom)
    r = client.post("/premium/chat", json={"question": "safe to run?"},
                    headers={"X-Internal-Secret": "test-secret"})
    assert r.status_code == 502

def test_stream_agent_failure_emits_error_frame(client, monkeypatch):
    """If run_agent raises, the stream must emit a single error frame, not crash."""
    import app.main as main

    async def boom(*args, **kwargs):
        raise RuntimeError("agent exploded")

    monkeypatch.setattr(main, "run_agent", boom)
    r = client.post("/premium/chat/stream", json={"question": "safe to run?"},
                    headers={"X-Internal-Secret": "test-secret"})
    assert r.status_code == 200                      # stream opens, then reports the error in-band
    frames = _parse_sse(r.text)
    assert any(f["type"] == "error" for f in frames)