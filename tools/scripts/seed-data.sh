#!/usr/bin/env bash
# Seed demo users and wellness records through the REST API.
#
# Usage:
#   ./tools/scripts/seed-data.sh                  (default: http://localhost:8080)
#   BASE_URL=http://localhost:8080 ./tools/scripts/seed-data.sh
#
# Requires:
#   • run.sh has been executed and Spring Boot is healthy.
#   • No arguments required; reads BASE_URL from environment or uses default.
#
# Demo password: Wellness@123
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

# HTTP calls.

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


# Each tuple: (days_ago, sleepHours, exerciseType|None, exerciseMinutes, moodScore, notes)
# Alice: excellent 7-day trend.
ALICE_RECORDS = [
    (0, "8.0", "Running",   45, 4, "Morning 5K, felt great"),
    (1, "8.5", "Cycling",   30, 5, "Long cycle — personal best"),
    (2, "7.8", "Walking",   60, 4, "Evening walk in the park"),
    (3, "8.2", "Yoga",      20, 4, "Restorative yoga session"),
    (4, "8.0", "Running",   50, 5, "Track interval training"),
    (5, "7.9", "Swimming",  40, 4, "50 laps at the pool"),
    (6, "8.1", "Walking",   35, 4, "Neighbourhood stroll"),
]

# Bob: low sleep, fair mood, some activity.
BOB_RECORDS = [
    (0, "5.5", "Walking",  20, 2, "Tired, only managed a short walk"),
    (1, "6.0", None,        0, 3, "Rest day — needed it"),
    (2, "5.0", None,        0, 2, "Terrible sleep, skipped exercise"),
    (3, "7.0", "Walking",  30, 3, "Feeling slightly better today"),
    (4, "6.5", None,        0, 2, "Work stress affecting sleep"),
    (5, "5.5", "Walking",  15, 3, "Quick walk around the block"),
    (6, "6.0", None,        0, 2, "Another rest day"),
]

# Carol: sparse current week plus older filter data.
CAROL_RECORDS = [
    # Sparse current week.
    (0,  "7.5", "Running",  30, 4, "Good morning run"),
    (2,  "7.0", "Yoga",     20, 3, "Gentle stretching"),
    (4,  "8.0", "Cycling",  45, 4, "Long bike ride along the coast"),
    (6,  "6.5", None,        0, 3, "Took a full rest day"),
    # Older filter data.
    (10, "7.0", "Walking",  30, 3, "Rainy day walk"),
    (12, "6.5", "Swimming", 40, 4, "Early morning swim"),
    (14, "8.0", None,        0, 5, "Perfect rest — slept in"),
]

# Demo: two records today for deduplication checks.
DEMO_RECORDS = [
    # Same-day records.
    (0, "6.0", "Walking", 15, 3, "Morning log — slow start"),
    (0, "8.0", "Running", 30, 5, "Evening log — feeling much better"),
    # Other days.
    (1, "7.5", "Cycling",  30, 4, "Solid session"),
    (2, "6.5", None,        0, 3, "Rest day"),
    (3, "7.0", "Running",  20, 3, "Light jog"),
    (4, "8.0", "Swimming", 45, 4, "Great swim session"),
]

# Premium: a couple of records to demo premium-routed chat.
PREMIUM_RECORDS = [
    (0, "7.5", "Running",  30, 4, "Morning run before the weather turned"),
    (1, "7.0", "Cycling",  40, 4, "Outdoor cycle — checked forecast first"),
]

print(f"\n  Wellness seed data loader")
print(f"  Target: {BASE_URL}\n")

# Verify backend health.
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
    ("Premium Pat", "premium@wellness.test", PREMIUM_RECORDS),
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

print("=" * 65)
print("  Demo credentials (password for all: Wellness@123)")
print("=" * 65)
print(f"  {'User':<14}  {'Email':<28}  Dashboard highlights")
print("-" * 65)
rows = [
    ("Alice Chen",  "alice@wellness.test",   "All EXCELLENT badges, full 7-day sparklines"),
    ("Bob Tan",     "bob@wellness.test",     "BELOW_TARGET sleep, FAIR mood, GOOD activity"),
    ("Carol Lim",   "carol@wellness.test",   "Sparse sparklines (4/7 days), date-filter demo"),
    ("Demo User",   "demo@wellness.test",    "Two records today → dedup avg in snapshot tile"),
    ("Premium Pat", "premium@wellness.test", "PREMIUM_USER — see promotion step below"),
]
for name, email, note in rows:
    print(f"  {name:<14}  {email:<28}  {note}")
print("=" * 65)
print()
print("  Android emulator API base URL: http://10.0.2.2:8080/")
print()

PYTHON

# Promote the demo premium account directly in MySQL (best-effort; no admin
# API exists to change roles yet, so this seed script talks to the database
# directly, same as a developer would via Adminer).
COMPOSE="${COMPOSE:-podman compose}"
if [[ -f ".env" ]]; then
  set -a; source ".env"; set +a
fi
MYSQL_DATABASE="${MYSQL_DATABASE:-wellness_app}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-change_me_root}"

echo "  Promoting premium@wellness.test to PREMIUM_USER..."
if $COMPOSE exec -T mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" \
     -e "UPDATE users SET role='PREMIUM_USER' WHERE email='premium@wellness.test';" 2>/dev/null; then
  echo "  ✓ Promoted premium@wellness.test → PREMIUM_USER"
else
  echo "  ! Could not reach the mysql container to promote premium@wellness.test."
  echo "    The account still works as a regular USER; promote manually via Adminer, or re-run:"
  echo "      \$COMPOSE exec -T mysql mysql -u root -p\"\$MYSQL_ROOT_PASSWORD\" \"\$MYSQL_DATABASE\" -e \"UPDATE users SET role='PREMIUM_USER' WHERE email='premium@wellness.test';\""
fi
echo
