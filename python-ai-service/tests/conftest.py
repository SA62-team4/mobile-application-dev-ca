"""Shared pytest fixtures for the RAG smoke tests.

Provides a deterministic, offline stand-in for Ollama so the indexing and
retrieval pipeline can be verified in CI without a live model server.

@author Chua Wei Yi Justin
"""

import hashlib
import math
import os
import re
from pathlib import Path

import pytest

# Silence Chroma's telemetry network calls during tests.
os.environ.setdefault("ANONYMIZED_TELEMETRY", "False")

# tests/ -> python-ai-service/ -> repo root
REPO_ROOT = Path(__file__).resolve().parents[2]
KNOWLEDGE_BASE_DIR = REPO_ROOT / "rag-knowledge-base"

_TOKEN = re.compile(r"[a-z0-9]+")


def _tokenize(text: str) -> list[str]:
    return [token for token in _TOKEN.findall(text.lower()) if len(token) > 2]


class FakeOllama:
    """Deterministic replacement for :class:`app.ollama_client.OllamaClient`.

    ``embed`` returns an L2-normalised bag-of-words vector using stable hashing,
    so text with overlapping vocabulary lands close together in Chroma (and a
    chunk's own text embeds to itself) without any model download or network. It
    also records call counts so tests can assert the pipeline embedded every
    chunk. ``generate`` returns a canned answer.
    """

    def __init__(self, dim: int = 256):
        self.dim = dim
        self.embed_calls = 0
        self.generate_calls = 0

    async def embed(self, text: str) -> list[float]:
        self.embed_calls += 1
        vector = [0.0] * self.dim
        for token in _tokenize(text):
            bucket = int(hashlib.md5(token.encode()).hexdigest(), 16) % self.dim
            vector[bucket] += 1.0
        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        return [value / norm for value in vector]

    async def generate(self, prompt: str, num_predict: int = 180) -> str:
        self.generate_calls += 1
        return "Deterministic test answer about wellness habits."

    async def generate_stream(self, prompt: str, num_predict: int = 220):
        """Yield the canned answer as a few fragments, mirroring Ollama streaming."""
        self.generate_calls += 1
        for fragment in ("Deterministic ", "streamed answer ", "about wellness habits."):
            yield fragment


@pytest.fixture
def fake_ollama() -> FakeOllama:
    return FakeOllama()


@pytest.fixture
def settings(tmp_path, monkeypatch):
    """Real Settings pointed at a temp Chroma dir and the real knowledge base."""
    monkeypatch.setenv("CHROMA_PERSIST_DIR", str(tmp_path / "chroma"))
    monkeypatch.setenv("KNOWLEDGE_BASE_DIR", str(KNOWLEDGE_BASE_DIR))
    from app.config import Settings

    return Settings()


@pytest.fixture
def rag_service(settings, fake_ollama):
    from app.rag_service import RagService

    return RagService(settings, fake_ollama)
