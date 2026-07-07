"""Unit tests for the Ollama HTTP client.

@author Tiong Zhong Cheng
"""

import httpx
import pytest

from app.config import Settings
from app.ollama_client import OllamaClient


def _client() -> OllamaClient:
    return OllamaClient(
        Settings(
            OLLAMA_BASE_URL="http://ollama.test",
            OLLAMA_GENERATION_MODEL="qwen2.5:1.5b",
            OLLAMA_EMBEDDING_MODEL="nomic-embed-text:latest",
        )
    )


def _patch_async_client(monkeypatch, transport: httpx.MockTransport) -> None:
    real_async_client = httpx.AsyncClient

    def factory(**kwargs):
        return real_async_client(transport=transport, **kwargs)

    monkeypatch.setattr("app.ollama_client.httpx.AsyncClient", factory)


async def test_embed_uses_current_ollama_embed_api(monkeypatch):
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["body"] = request.read()
        return httpx.Response(200, json={"embeddings": [[0.1, 0.2, 0.3]]})

    _patch_async_client(monkeypatch, httpx.MockTransport(handler))

    assert await _client().embed("sleep habits") == [0.1, 0.2, 0.3]
    assert seen["path"] == "/api/embed"
    assert b'"input":"sleep habits"' in seen["body"]


async def test_embed_falls_back_to_legacy_embeddings_api_on_404(monkeypatch):
    paths = []

    def handler(request: httpx.Request) -> httpx.Response:
        paths.append(request.url.path)
        if request.url.path == "/api/embed":
            return httpx.Response(404, request=request)
        return httpx.Response(200, json={"embedding": [0.4, 0.5]})

    _patch_async_client(monkeypatch, httpx.MockTransport(handler))

    assert await _client().embed("sleep habits") == [0.4, 0.5]
    assert paths == ["/api/embed", "/api/embeddings"]


async def test_embed_reraises_non_404_errors(monkeypatch):
    _patch_async_client(monkeypatch, httpx.MockTransport(lambda request: httpx.Response(500, request=request)))

    with pytest.raises(httpx.HTTPStatusError):
        await _client().embed("sleep habits")


async def test_generate_posts_bounded_non_streaming_request(monkeypatch):
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["body"] = request.read()
        return httpx.Response(200, json={"response": "  Generated answer.  "})

    _patch_async_client(monkeypatch, httpx.MockTransport(handler))

    assert await _client().generate("prompt", num_predict=77) == "Generated answer."
    assert seen["path"] == "/api/generate"
    assert b'"stream":false' in seen["body"]
    assert b'"num_predict":77' in seen["body"]
    assert b'"num_ctx":1024' in seen["body"]


async def test_generate_stream_yields_response_fragments(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            content=b'{"response":"Sleep"}\n\n{"response":" well"}\n{"done":true}\n',
            request=request,
        )

    _patch_async_client(monkeypatch, httpx.MockTransport(handler))

    fragments = [fragment async for fragment in _client().generate_stream("prompt")]

    assert fragments == ["Sleep", " well"]


async def test_generate_stream_raises_with_response_body_on_error(monkeypatch):
    _patch_async_client(
        monkeypatch,
        httpx.MockTransport(lambda request: httpx.Response(503, content=b"ollama down", request=request)),
    )

    with pytest.raises(httpx.HTTPStatusError, match="ollama down"):
        [fragment async for fragment in _client().generate_stream("prompt")]
