#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# seed-data.sh — register demo users and load wellness records via the REST API
#
# Usage:
#   ./tools/scripts/seed-data.sh                  (default: http://localhost:8080)
#   BASE_URL=http://localhost:8080 ./tools/scripts/seed-data.sh
#
# Pre-conditions:
#   • run.sh has been executed and Spring Boot is healthy.
#   • No arguments required; reads BASE_URL from environment or uses default.
#
# Demo users created (all passwords: Wellness@123)
#   alice@wellness.test  — All-excellent badges  (7 consecutive days, high sleep/mood/activity)
#   bob@wellness.test    — Mixed/below-target    (7 days, poor sleep, low mood, some activity)
#   carol@wellness.test  — Sparse + historical   (4 of 7 days this week + older records for date filter)
#   demo@wellness.test   — Dedup edge case       (two records on today, exercises dedup logic)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

python3 - "$BASE_URL" <<'PYTHON'
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta

BASE_URL = sys.argv[1].rstrip("/")
PASSWORD = "Wellness@123"

# ── HTTP helpers ──────────────────────────────────────────────────────────────

def http(method, path, body=None, token=None):
    """Make an HTTP request; return (status, parsed_body)."""
    url = f"{BASE_URL}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw.strip() else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        return e.code, json.loads(raw) if raw.strip() else {}


def register_or_login(display_name, email, password):
    """Register a new user; if already exists, just log in."""
    status, body = http("POST", "/api/auth/register", {
        "displayName": display_name,
        "email": email,
        "password": password,
    })
    if status == 201:
        print(f"  ✓ Registered  {email}")
    elif status in (400, 409):
        print(f"  · Exists      {email} — logging in instead")
    else:
        raise RuntimeError(f"Unexpected register status {status}: {body}")

    status, body = http("POST", "/api/auth/login", {
        "email": email,
        "password": password,
    })
    if status != 200:
        raise RuntimeError(f"Login failed for {email}: {status} {body}")
    return body["token"]


def create_record(token, days_ago, sleep_hours, exercise_type, exercise_minutes, mood_score, notes):
    """POST a single wellness record."""
    record_date = (date.today() - timedelta(days=days_ago)).isoformat()
    payload = {
        "recordDate": record_date,
        "sleepHours": sleep_hours,
        "exerciseType": exercise_type,
        "exerciseMinutes": exercise_minutes,
        "moodScore": mood_score,
        "notes": notes,
    }
    status, body = http("POST", "/api/wellness-records", payload, token)
    if status not in (200, 201):
        raise RuntimeError(f"Record creation failed ({status}): {body}")
    return body


def seed_user(display_name, email, records):
    """Register/login a user and create all their wellness records."""
    token = register_or_login(display_name, email, PASSWORD)
    for rec in records:
        days_ago, sleep, ex_type, ex_min, mood, notes = rec
        create_record(token, days_ago, sleep, ex_type, ex_min, mood, notes)
    print(f"    → {len(records)} records created")
    return token


# ── Data design ───────────────────────────────────────────────────────────────
#
# Each tuple: (days_ago, sleepHours, exerciseType|None, exerciseMinutes, moodScore, notes)
#
# Alice  — EXCELLENT all badges
#   sleep avg  : (8.0+8.5+7.8+8.2+8.0+7.9+8.1)/7 = 8.07 → round1dp=8.1 → EXCELLENT (≥8.0)
#   active days: 7 out of 7                                              → EXCELLENT (≥5)
#   mood avg   : (4+5+4+4+5+4+4)/7 = 4.29 → round1dp=4.3              → EXCELLENT (≥4.0)
ALICE_RECORDS = [
    (0, "8.0", "Running",   45, 4, "Morning 5K, felt great"),
    (1, "8.5", "Cycling",   30, 5, "Long cycle — personal best"),
    (2, "7.8", "Walking",   60, 4, "Evening walk in the park"),
    (3, "8.2", "Yoga",      20, 4, "Restorative yoga session"),
    (4, "8.0", "Running",   50, 5, "Track interval training"),
    (5, "7.9", "Swimming",  40, 4, "50 laps at the pool"),
    (6, "8.1", "Walking",   35, 4, "Neighbourhood stroll"),
]

# Bob    — BELOW_TARGET sleep, FAIR mood, GOOD activity
#   sleep avg  : (5.5+6.0+5.0+7.0+6.5+5.5+6.0)/7 = 5.93 → round1dp=5.9 → BELOW_TARGET (<6.0)
#   active days: days 0, 3, 5 have exercise                              → GOOD (3–4 days)
#   mood avg   : (2+3+2+3+2+3+2)/7 = 2.43 → round1dp=2.4              → FAIR (2.0–2.9)
BOB_RECORDS = [
    (0, "5.5", "Walking",  20, 2, "Tired, only managed a short walk"),
    (1, "6.0", None,        0, 3, "Rest day — needed it"),
    (2, "5.0", None,        0, 2, "Terrible sleep, skipped exercise"),
    (3, "7.0", "Walking",  30, 3, "Feeling slightly better today"),
    (4, "6.5", None,        0, 2, "Work stress affecting sleep"),
    (5, "5.5", "Walking",  15, 3, "Quick walk around the block"),
    (6, "6.0", None,        0, 2, "Another rest day"),
]

# Carol  — Sparse sparklines (4 of 7 days) + historical records for date-filter demo
#   This week (days 0, 2, 4, 6 only — every other day):
#   sleep avg  : (7.5+7.0+8.0+6.5)/4 = 7.25 → round1dp=7.3 → GOOD (7.0–7.9)
#   active days: days 0, 2, 4 have exercise (day 6 is rest) → 3 → GOOD (3–4 days)
#   mood avg   : (4+3+4+3)/4 = 3.5 → round1dp=3.5          → GOOD (3.0–3.9)
#   Older records (days 10, 12, 14) — visible only when date-filter spans that range
CAROL_RECORDS = [
    # This week — sparse (every other day)
    (0,  "7.5", "Running",  30, 4, "Good morning run"),
    (2,  "7.0", "Yoga",     20, 3, "Gentle stretching"),
    (4,  "8.0", "Cycling",  45, 4, "Long bike ride along the coast"),
    (6,  "6.5", None,        0, 3, "Took a full rest day"),
    # Older — only visible when filter applied (use date filter: today-14 → today-9)
    (10, "7.0", "Walking",  30, 3, "Rainy day walk"),
    (12, "6.5", "Swimming", 40, 4, "Early morning swim"),
    (14, "8.0", None,        0, 5, "Perfect rest — slept in"),
]

# Demo   — Deduplication edge case: 2 records on today's date
#   DashboardDataHelper dedup for today: sleep=(6.0+8.0)/2=7.0, exercise=15+30=45min, mood=(3+5)/2=4
#   Overall weekly (5 distinct dates including deduped today):
#   sleep avg  : (7.0+7.5+6.5+7.0+8.0)/5 = 7.2 → round1dp=7.2 → GOOD
#   active days: today-45min, day1-30min, day2-rest, day3-20min, day4-45min → 4 → GOOD
#   mood avg   : (4+4+3+3+4)/5 = 3.6 → round1dp=3.6               → GOOD
DEMO_RECORDS = [
    # Two records today — tests DashboardDataHelper.aggregateByDate deduplication
    (0, "6.0", "Walking", 15, 3, "Morning log — slow start"),
    (0, "8.0", "Running", 30, 5, "Evening log — feeling much better"),
    # Other days
    (1, "7.5", "Cycling",  30, 4, "Solid session"),
    (2, "6.5", None,        0, 3, "Rest day"),
    (3, "7.0", "Running",  20, 3, "Light jog"),
    (4, "8.0", "Swimming", 45, 4, "Great swim session"),
]

# ── Run ───────────────────────────────────────────────────────────────────────

print(f"\n  Wellness seed data loader")
print(f"  Target: {BASE_URL}\n")

# Verify backend is reachable
status, health = http("GET", "/actuator/health")
if status != 200 or health.get("status") != "UP":
    print(f"  ✗ Backend not healthy ({status}: {health}). Is run.sh complete?")
    sys.exit(1)
print(f"  ✓ Backend healthy\n")

users = [
    ("Alice Chen",  "alice@wellness.test",  ALICE_RECORDS),
    ("Bob Tan",     "bob@wellness.test",     BOB_RECORDS),
    ("Carol Lim",   "carol@wellness.test",   CAROL_RECORDS),
    ("Demo User",   "demo@wellness.test",    DEMO_RECORDS),
]

tokens = {}
for display_name, email, records in users:
    print(f"  [{display_name}]")
    try:
        tokens[email] = seed_user(display_name, email, records)
    except RuntimeError as e:
        print(f"  ✗ {e}")
        sys.exit(1)
    print()

# ── Credentials table ─────────────────────────────────────────────────────────
print("=" * 65)
print("  Demo credentials (password for all: Wellness@123)")
print("=" * 65)
print(f"  {'User':<14}  {'Email':<28}  Dashboard highlights")
print("-" * 65)
rows = [
    ("Alice Chen",  "alice@wellness.test",  "All EXCELLENT badges, full 7-day sparklines"),
    ("Bob Tan",     "bob@wellness.test",    "BELOW_TARGET sleep, FAIR mood, GOOD activity"),
    ("Carol Lim",   "carol@wellness.test",  "Sparse sparklines (4/7 days), date-filter demo"),
    ("Demo User",   "demo@wellness.test",   "Two records today → dedup avg in snapshot tile"),
]
for name, email, note in rows:
    print(f"  {name:<14}  {email:<28}  {note}")
print("=" * 65)
print()
print("  Android emulator API base URL: http://10.0.2.2:8080/")
print()

PYTHON
