#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

python3 - "$BASE_URL" <<'PY'
import json
import sys
import urllib.error
import urllib.request

base_url = sys.argv[1].rstrip("/")

def get_json(path, headers=None):
    request = urllib.request.Request(f"{base_url}{path}", headers=headers or {})
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status, json.loads(response.read().decode("utf-8"))

status, root = get_json("/")
assert status == 200, status
assert root == {"service": "wellness-backend", "status": "UP", "health": "/actuator/health"}, root

status, health = get_json("/actuator/health")
assert status == 200, status
assert health == {"status": "UP"}, health

request = urllib.request.Request(
    f"{base_url}/api/internal/users/1/wellness-records?days=1",
    headers={"X-Internal-Service-Token": "invalid-token"},
)
try:
    urllib.request.urlopen(request, timeout=10)
except urllib.error.HTTPError as error:
    assert error.code == 403, error.code
else:
    raise AssertionError("Internal endpoint accepted an invalid service token")

print(f"Contract smoke checks passed for {base_url}")
PY
