"""CI-safe coverage for RAG edge and fallback paths (Ollama mocked).

Covers the branches the happy-path smoke tests do not exercise:
- reindex rejects an empty knowledge base
- reindex clears previously indexed ids before rebuilding
- chat / chat_stream emit a graceful fallback when generation is empty
- the stream-answer reducer skips blank SSE frames

@author Tiong Zhong Cheng
"""

import json

import pytest
from fastapi import HTTPException

from app.models import RagChatRequest
from app.rag_service import _reduce_stream_answer
from tests.conftest import FakeOllama


class EmptyGenerationOllama(FakeOllama):
    """FakeOllama whose generation produces nothing, forcing the fallbacks."""

    async def generate(self, prompt: str, num_predict: int = 180) -> str:
        self.generate_calls += 1
        return ""

    async def generate_stream(self, prompt: str, num_predict: int = 220):
        self.generate_calls += 1
        return
        yield  # pragma: no cover - makes this an async generator


async def test_reindex_rejects_empty_knowledge_base(rag_service, monkeypatch):
    monkeypatch.setattr("app.rag_service.load_chunks", lambda _dir: [])

    with pytest.raises(HTTPException) as excinfo:
        await rag_service.reindex()

    assert excinfo.value.status_code == 400


async def test_reindex_clears_existing_ids_before_rebuild(rag_service):
    first = await rag_service.reindex()
    # Re-running must delete the prior ids and rebuild to the same count.
    second = await rag_service.reindex()

    assert second == first
    assert rag_service.collection.count() == first["chunks"]


async def test_chat_returns_fallback_when_generation_empty(settings):
    from app.rag_service import RagService

    rag = RagService(settings, EmptyGenerationOllama())

    response = await rag.chat(RagChatRequest(userId=1, question="How can I sleep better?"))

    assert "could not generate a response" in response.answer.lower()


async def test_chat_stream_emits_fallback_token_when_no_output(settings):
    from app.rag_service import RagService

    rag = RagService(settings, EmptyGenerationOllama())

    frames = [
        json.loads(frame.removeprefix("data:").strip())
        async for frame in rag.chat_stream(
            RagChatRequest(userId=1, question="How can I sleep better?")
        )
    ]

    tokens = [frame["text"] for frame in frames if frame["type"] == "token"]
    assert tokens
    assert "could not generate a response" in " ".join(tokens).lower()
    assert frames[-1]["type"] == "done"


def test_reduce_stream_answer_skips_blank_frames():
    frames = [
        "data: \n\n",  # blank payload -> skipped
        'data: {"type": "sources", "sources": []}\n\n',  # non-token -> ignored
        'data: {"type": "token", "text": "hello "}\n\n',
        'data: {"type": "token", "text": "world"}\n\n',
    ]

    assert _reduce_stream_answer(frames) == "hello world"
