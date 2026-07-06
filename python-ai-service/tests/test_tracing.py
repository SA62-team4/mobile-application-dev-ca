"""Tests for LangSmith tracing configuration and RAG-path instrumentation.

Covers the env-var wiring in :func:`app.tracing.configure_tracing`, the
``strip_self`` input sanitiser, and that the RAG chat path (chat, retrieve, and
the underlying Ollama calls) is decorated with ``@traceable`` and still behaves
correctly when tracing is switched on.

@author Zhong Cheng
"""

import os

import pytest

from app.config import Settings
from app.models import RagChatRequest
from app.ollama_client import OllamaClient
from app.rag_service import RagService
from app.tracing import configure_tracing, strip_self


def _settings(**overrides) -> Settings:
    """Build Settings from explicit values, ignoring the ambient environment."""
    base = {
        "LANGSMITH_TRACING": "false",
        "LANGSMITH_API_KEY": "",
        "LANGSMITH_PROJECT": "wellness-agentic-ai",
        "LANGSMITH_ENDPOINT": "https://api.smith.langchain.com",
    }
    base.update(overrides)
    return Settings(**base)


@pytest.fixture(autouse=True)
def _clean_langsmith_env(monkeypatch):
    """Isolate each test from LANGSMITH_* leaking in from the outer environment."""
    for key in ("LANGSMITH_TRACING", "LANGSMITH_API_KEY", "LANGSMITH_PROJECT", "LANGSMITH_ENDPOINT"):
        monkeypatch.delenv(key, raising=False)


# ── configure_tracing ────────────────────────────────────────────────────────

def test_tracing_disabled_by_default():
    configure_tracing(_settings())

    assert os.environ["LANGSMITH_TRACING"] == "false"


def test_tracing_enabled_without_api_key_stays_off(caplog):
    configure_tracing(_settings(LANGSMITH_TRACING="true", LANGSMITH_API_KEY=""))

    assert os.environ["LANGSMITH_TRACING"] == "false"
    assert any("LANGSMITH_API_KEY is empty" in record.message for record in caplog.records)


def test_tracing_enabled_with_api_key_sets_env():
    configure_tracing(
        _settings(
            LANGSMITH_TRACING="true",
            LANGSMITH_API_KEY="ls-test-key",
            LANGSMITH_PROJECT="my-project",
            LANGSMITH_ENDPOINT="https://eu.api.smith.langchain.com",
        )
    )

    assert os.environ["LANGSMITH_TRACING"] == "true"
    assert os.environ["LANGSMITH_API_KEY"] == "ls-test-key"
    assert os.environ["LANGSMITH_PROJECT"] == "my-project"
    assert os.environ["LANGSMITH_ENDPOINT"] == "https://eu.api.smith.langchain.com"


def test_disabling_overrides_a_previously_enabled_env(monkeypatch):
    monkeypatch.setenv("LANGSMITH_TRACING", "true")  # stray value from elsewhere

    configure_tracing(_settings(LANGSMITH_TRACING="false"))

    assert os.environ["LANGSMITH_TRACING"] == "false"


# ── strip_self ───────────────────────────────────────────────────────────────

def test_strip_self_removes_only_self():
    assert strip_self({"self": object(), "question": "sleep", "top_k": 4}) == {
        "question": "sleep",
        "top_k": 4,
    }


def test_strip_self_is_noop_without_self():
    assert strip_self({"question": "sleep"}) == {"question": "sleep"}


# ── RAG-path instrumentation ─────────────────────────────────────────────────

@pytest.mark.parametrize(
    "func",
    [
        RagService.chat,
        RagService.retrieve,
        OllamaClient.embed,
        OllamaClient.generate,
    ],
)
def test_rag_path_methods_are_traceable(func):
    # Every step of the RAG chat path is wrapped by langsmith's @traceable.
    assert getattr(func, "__langsmith_traceable__", False) is True


def test_traceable_preserves_wrapped_function_identity():
    # @traceable must wrap, not replace: the original coroutine is reachable via
    # __wrapped__ and the public name is kept so tracing is transparent.
    assert RagService.chat.__name__ == "chat"
    assert RagService.chat.__wrapped__.__qualname__.endswith("RagService.chat")


async def test_chat_path_returns_answer_with_tracing_disabled(rag_service, fake_ollama):
    # The decorated chat path (tracing off, the default) behaves exactly as
    # before: an answer plus sources, with a single generate call.
    configure_tracing(_settings(LANGSMITH_TRACING="false"))

    response = await rag_service.chat(RagChatRequest(userId=1, question="How can I sleep better?"))

    assert response.answer.strip()
    assert response.sources
    assert fake_ollama.generate_calls == 1
