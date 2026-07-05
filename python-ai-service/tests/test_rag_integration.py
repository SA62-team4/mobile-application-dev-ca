"""Opt-in live RAG integration test (requires a running Ollama).

These hit a real Ollama server and the configured embedding model, so they are
excluded from CI. Run locally once Ollama is up and the models are pulled:

    RUN_OLLAMA_INTEGRATION=1 pytest -m integration

CI runs `pytest -m "not integration"`, which skips this module entirely.

@author JustinChua97
"""

import os
from pathlib import Path

import pytest

pytestmark = pytest.mark.integration

REPO_ROOT = Path(__file__).resolve().parents[2]


@pytest.fixture
def live_settings(tmp_path, monkeypatch):
    if os.getenv("RUN_OLLAMA_INTEGRATION") != "1":
        pytest.skip("set RUN_OLLAMA_INTEGRATION=1 to run live Ollama integration tests")

    monkeypatch.setenv("CHROMA_PERSIST_DIR", str(tmp_path / "chroma"))
    monkeypatch.setenv("KNOWLEDGE_BASE_DIR", str(REPO_ROOT / "rag-knowledge-base"))
    from app.config import Settings

    return Settings()


async def test_live_reindex_and_retrieve(live_settings):
    from app.ollama_client import OllamaClient
    from app.rag_service import RagService

    rag = RagService(live_settings, OllamaClient(live_settings))

    result = await rag.reindex()
    assert result["chunks"] > 0

    chunks = await rag.retrieve("how many hours should I sleep", top_k=4)
    assert chunks
    assert any("sleep" in chunk.source_file for chunk in chunks)


async def test_live_chat_answers_with_sources(live_settings):
    from app.models import RagChatRequest
    from app.ollama_client import OllamaClient
    from app.rag_service import RagService

    rag = RagService(live_settings, OllamaClient(live_settings))

    response = await rag.chat(RagChatRequest(userId=1, question="How can I sleep better?"))
    assert response.answer.strip()
    assert response.sources
