"""Opt-in LIVE end-to-end test for the agentic recommendation workflow.

This hits a REAL running stack: the Python AI service (FastAPI + local Ollama)
and the Spring backend (+ MySQL). It is the live counterpart to the offline
``test_agent_recommendation_e2e.py`` and proves the ticket acceptance against
real infrastructure: an end-to-end generate call writes a recommendation row
and returns it.

Run locally, once the full stack is up (`docker compose up`) and the Ollama
generation model is pulled:

    RUN_LIVE_INTEGRATION=1 pytest -m integration tests/test_agent_recommendation_live.py

CI runs `pytest -m "not integration"`, so this module never runs in CI.

Optional env overrides (defaults target a local docker-compose stack):

    AI_BASE_URL            default http://localhost:8000
    SPRING_BASE_URL        default http://localhost:8080
    LIVE_GENERATE_TIMEOUT  default 180 (seconds; CPU Ollama is slow)

Safety design — non-destructive under ALL conditions:

* Double opt-in: the ``integration`` marker AND ``RUN_LIVE_INTEGRATION=1``. Absent
  either, the test SKIPS and touches nothing. CI has neither, so it is inert there.
* Read-only preflight health checks. If any part of the stack is not fully
  reachable/UP, the test SKIPS (it never fails and never writes anything).
* Every write is isolated to a throwaway account created per run with a unique
  email, so the test can never read, modify, or affect a real user's data.
* Best-effort cleanup removes the wellness records it created. The single
  recommendation row and the throwaway user have no delete API, but they are
  fully isolated and cannot change behaviour for any other user.
* No secrets are embedded. The test uses only the public/authenticated API and
  the AI endpoint; it never reads the internal service token, never touches
  config, never reindexes the shared vector store, and never disables auth.
* All calls are bounded by timeouts, so the suite can never hang.

@author Chua Wei Yi Justin
"""

import datetime as dt
import os
import uuid

import httpx
import pytest

pytestmark = pytest.mark.integration

AI_BASE_URL = os.getenv("AI_BASE_URL", "http://localhost:8000").rstrip("/")
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080").rstrip("/")

# Generous but bounded: CPU-only Ollama generation on a small droplet is slow.
GENERATE_TIMEOUT = float(os.getenv("LIVE_GENERATE_TIMEOUT", "180"))
QUICK_TIMEOUT = 10.0

# Substrings that identify the documented .NET-backup footgun in a 502 detail
# (DNS resolution failures and the backup host name).
_FOOTGUN_MARKERS = (
    "name or service not known",
    "getaddrinfo",
    "nodename nor servname",
    "dotnet",
)


def _require_live_stack() -> None:
    """Opt-in gate + read-only preflight. Skips (never fails) if not ready."""
    if os.getenv("RUN_LIVE_INTEGRATION") != "1":
        pytest.skip(
            "set RUN_LIVE_INTEGRATION=1 and start the full stack to run live e2e tests"
        )
    try:
        ai_health = httpx.get(f"{AI_BASE_URL}/health", timeout=QUICK_TIMEOUT)
        spring_health = httpx.get(f"{SPRING_BASE_URL}/actuator/health", timeout=QUICK_TIMEOUT)
    except httpx.HTTPError as exc:
        pytest.skip(f"live stack not reachable ({exc}); skipping")
    if ai_health.status_code != 200 or ai_health.json().get("status") != "UP":
        pytest.skip("python-ai-service /health is not UP; skipping")
    if spring_health.status_code != 200:
        pytest.skip("spring-backend /actuator/health is not UP; skipping")


def _register_and_login(client: httpx.Client) -> tuple[int, str]:
    """Create an isolated throwaway user and log in. Returns (user_id, jwt)."""
    unique = uuid.uuid4().hex[:12]
    email = f"live-int+{unique}@example.test"
    password = f"Pw-{uuid.uuid4().hex}"  # random per run; never a real credential

    register = client.post(
        f"{SPRING_BASE_URL}/api/auth/register",
        json={"displayName": f"Live Integration {unique}", "email": email, "password": password},
    )
    register.raise_for_status()
    user_id = register.json()["id"]

    login = client.post(
        f"{SPRING_BASE_URL}/api/auth/login",
        json={"email": email, "password": password},
    )
    login.raise_for_status()
    return user_id, login.json()["token"]


def _seed_low_sleep_records(client: httpx.Client, auth: dict) -> list[int]:
    """Log a few recent low-sleep records so trend analysis has data to read."""
    today = dt.date.today()
    seed = [
        {"days_ago": 1, "sleepHours": 6.0, "exerciseMinutes": 30, "moodScore": 4},
        {"days_ago": 2, "sleepHours": 6.5, "exerciseMinutes": 20, "moodScore": 4},
        {"days_ago": 3, "sleepHours": 6.8, "exerciseMinutes": 15, "moodScore": 4},
    ]
    created: list[int] = []
    for item in seed:
        record = client.post(
            f"{SPRING_BASE_URL}/api/wellness-records",
            headers=auth,
            json={
                "recordDate": (today - dt.timedelta(days=item["days_ago"])).isoformat(),
                "sleepHours": item["sleepHours"],
                "exerciseType": "walk",
                "exerciseMinutes": item["exerciseMinutes"],
                "moodScore": item["moodScore"],
                "notes": "live integration seed",
            },
        )
        record.raise_for_status()
        created.append(record.json()["id"])
    return created


def _cleanup_records(client: httpx.Client, auth: dict, record_ids: list[int]) -> None:
    """Best-effort deletion of seeded records. Never raises."""
    for record_id in record_ids:
        try:
            client.delete(
                f"{SPRING_BASE_URL}/api/wellness-records/{record_id}",
                headers=auth,
                timeout=QUICK_TIMEOUT,
            )
        except httpx.HTTPError:
            pass  # cleanup is best-effort; isolated data is harmless if it lingers.


def test_live_generate_writes_a_recommendation_row_and_returns_it():
    _require_live_stack()

    created_record_ids: list[int] = []
    with httpx.Client(timeout=QUICK_TIMEOUT) as client:
        try:
            user_id, token = _register_and_login(client)
        except httpx.HTTPError as exc:
            pytest.skip(f"could not set up throwaway account on live stack ({exc}); skipping")

        auth = {"Authorization": f"Bearer {token}"}
        try:
            created_record_ids = _seed_low_sleep_records(client, auth)

            # Act: trigger the real agent (fetch -> analyse -> RAG -> Ollama -> save).
            try:
                response = client.post(
                    f"{AI_BASE_URL}/agent/recommendation/{user_id}",
                    timeout=GENERATE_TIMEOUT,
                )
            except (httpx.ConnectError, httpx.TimeoutException) as exc:
                pytest.skip(f"agent generate call did not complete ({exc}); stack not ready")

            # A 502 means the agent workflow could not reach Spring or Ollama.
            if response.status_code == 502:
                detail = str(response.json().get("detail", "")).lower()
                if any(marker in detail for marker in _FOOTGUN_MARKERS):
                    pytest.fail(
                        "Recommendation save hit the .NET-backup footgun "
                        f"(BACKEND_BASE_URL likely misconfigured): {detail}"
                    )
                pytest.skip(f"agent workflow 502 (Ollama likely unavailable): {detail}")

            assert response.status_code == 200, f"unexpected status: {response.text}"
            body = response.json()

            # The saved row is returned with a populated id ("returns it").
            assert body["id"] is not None
            assert body["generatedBy"] == "python-agent"
            assert body["title"].strip()
            assert body["recommendationText"].strip()
            assert body["trendSummary"].strip()
            assert len(body["actionItems"]) == 3

            # The row is actually persisted in MySQL under this user ("saves").
            listing = client.get(f"{SPRING_BASE_URL}/api/recommendations", headers=auth)
            listing.raise_for_status()
            saved_ids = {row["id"] for row in listing.json()}
            assert body["id"] in saved_ids
        finally:
            _cleanup_records(client, auth, created_record_ids)
