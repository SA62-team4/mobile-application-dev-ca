"""Unit tests for Spring internal API client calls.

@author Tiong Zhong Cheng
"""

import httpx
import pytest

from app.backend_client import BackendClient
from app.config import Settings
from app.models import InternalRecommendationRequest


def _client(token: str = "test-token") -> BackendClient:
    return BackendClient(
        Settings(
            BACKEND_BASE_URL="http://backend.test",
            INTERNAL_SERVICE_TOKEN=token,
        )
    )


def _patch_async_client(monkeypatch, transport: httpx.MockTransport) -> None:
    real_async_client = httpx.AsyncClient

    def factory(**kwargs):
        return real_async_client(transport=transport, **kwargs)

    monkeypatch.setattr("app.backend_client.httpx.AsyncClient", factory)


async def test_recent_records_sends_internal_token_and_days(monkeypatch):
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["token"] = request.headers["X-Internal-Service-Token"]
        return httpx.Response(
            200,
            json=[{"recordDate": "2026-07-01", "sleepHours": 7.5, "exerciseMinutes": 30, "moodScore": 4}],
        )

    _patch_async_client(monkeypatch, httpx.MockTransport(handler))

    records = await _client().recent_records(42, days=5)

    assert records[0]["sleepHours"] == 7.5
    assert seen["url"] == "http://backend.test/api/internal/users/42/wellness-records?days=5"
    assert seen["token"] == "test-token"


async def test_save_recommendation_posts_payload_and_validates_response(monkeypatch):
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["token"] = request.headers["X-Internal-Service-Token"]
        seen["body"] = request.read()
        return httpx.Response(
            201,
            json={
                "id": 123,
                "title": "Sleep plan",
                "trendSummary": "Sleep was low.",
                "recommendationText": "Wind down earlier.",
                "actionItems": ["Set bedtime", "Dim lights", "Log tomorrow"],
                "generatedBy": "python-agent",
                "createdAt": "2026-07-01T12:00:00Z",
            },
        )

    _patch_async_client(monkeypatch, httpx.MockTransport(handler))

    response = await _client("secret").save_recommendation(
        12,
        InternalRecommendationRequest(
            title="Sleep plan",
            trendSummary="Sleep was low.",
            recommendationText="Wind down earlier.",
            actionItems=["Set bedtime", "Dim lights", "Log tomorrow"],
        ),
    )

    assert response.id == 123
    assert response.generatedBy == "python-agent"
    assert seen["url"] == "http://backend.test/api/internal/users/12/recommendations"
    assert seen["token"] == "secret"
    assert b'"title":"Sleep plan"' in seen["body"]
    assert b'"generatedBy":"python-agent"' in seen["body"]


async def test_backend_http_errors_are_not_swallowed(monkeypatch):
    _patch_async_client(monkeypatch, httpx.MockTransport(lambda request: httpx.Response(503)))

    with pytest.raises(httpx.HTTPStatusError):
        await _client().recent_records(1)
