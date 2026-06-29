#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run.sh — start the Wellness App with podman compose
#
# Usage:
#   ./run.sh                  Normal start (incremental; reuses running state)
#   ./run.sh --clean          Wipe app data (MySQL + ChromaDB) and rebuild
#                             images. Preserves ollama-data (avoids re-downloading
#                             multi-GB LLM models).
#   ./run.sh --clean-all      Wipe EVERYTHING including ollama-data.
#                             Ollama models will be re-downloaded on startup.
#   ./run.sh --skip-models    Skip the Ollama model presence check.
#   ./run.sh -h               Show this help.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"  # ensures .env and docker-compose.yml are found automatically

COMPOSE="podman compose"

# ── terminal colours ──────────────────────────────────────────────────────────
if [[ -t 1 ]]; then
  GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'
  BOLD='\033[1m'; DIM='\033[2m'; NC='\033[0m'
else
  GREEN=''; YELLOW=''; RED=''; BOLD=''; DIM=''; NC=''
fi

info()  { echo -e "${GREEN}  ✓${NC}  $*"; }
warn()  { echo -e "${YELLOW}  !${NC}  $*"; }
error() { echo -e "${RED}  ✗${NC}  $*" >&2; }
step()  { echo -e "\n${BOLD}▶  $*${NC}"; }
dim()   { echo -e "${DIM}     $*${NC}"; }

# ── arg parsing ───────────────────────────────────────────────────────────────
CLEAN=false
CLEAN_ALL=false
SKIP_MODELS=false

usage() {
  sed -n '2,15p' "$0" | sed 's/^# \?//'
}

for arg in "$@"; do
  case "$arg" in
    --clean)       CLEAN=true ;;
    --clean-all)   CLEAN=true; CLEAN_ALL=true ;;
    --skip-models) SKIP_MODELS=true ;;
    -h|--help)     usage; exit 0 ;;
    *)             error "Unknown argument: $arg"; echo ""; usage; exit 1 ;;
  esac
done

# ── source .env for port numbers (read-only — does not modify env in parent) ──
if [[ -f ".env" ]]; then
  set -a; source ".env"; set +a
else
  warn ".env not found — using defaults. Copy .env.example to .env and fill in values."
fi

SPRING_PORT="${SPRING_HOST_PORT:-8080}"
OLLAMA_PORT="${OLLAMA_HOST_PORT:-11434}"
AI_PORT="${AI_SERVICE_HOST_PORT:-8000}"
ADMINER_PORT="${ADMINER_HOST_PORT:-8081}"
GEN_MODEL="${OLLAMA_GENERATION_MODEL:-llama3.2:3b}"
EMB_MODEL="${OLLAMA_EMBEDDING_MODEL:-nomic-embed-text}"

# ── helper: wait for HTTP endpoint to return 2xx ─────────────────────────────
wait_for_url() {
  local url="$1" label="$2" max_wait="${3:-180}" interval=5
  local elapsed=0
  printf "     Waiting for %-30s" "$label"
  while ! curl -sf --max-time 3 "$url" > /dev/null 2>&1; do
    if [[ $elapsed -ge $max_wait ]]; then
      echo ""
      error "$label did not become healthy after ${max_wait}s."
      error "Check logs: $COMPOSE logs <service>"
      return 1
    fi
    printf "."
    sleep "$interval"
    elapsed=$((elapsed + interval))
  done
  echo -e "  ${GREEN}ready${NC}"
}

# ── macOS: ensure Podman machine is running ───────────────────────────────────
if [[ "$(uname)" == "Darwin" ]]; then
  step "Podman machine (macOS)"
  STATUS=$(podman machine status 2>/dev/null | awk '{print $2}' || echo "Unknown")
  if [[ "$STATUS" != "Running" ]]; then
    warn "Podman machine is not running (status: $STATUS). Starting it..."
    podman machine start
    info "Podman machine started."
  else
    info "Podman machine is running."
  fi
fi

# ── clean ─────────────────────────────────────────────────────────────────────
if $CLEAN; then
  step "Cleaning previous run"
  warn "Stopping all containers..."
  $COMPOSE down --timeout 15 2>/dev/null || true

  if $CLEAN_ALL; then
    warn "Removing ALL volumes (ollama-data included — models will re-download)."
    # --volumes removes volumes declared in the compose file
    $COMPOSE down --volumes --rmi local 2>/dev/null || true
    # Belt-and-suspenders: also remove by pattern in case already stopped
    podman volume ls --format "{{.Name}}" 2>/dev/null \
      | grep -E "(mysql-data|ollama-data|chroma-data)" \
      | xargs -r podman volume rm 2>/dev/null || true
  else
    warn "Removing app data volumes (mysql-data + chroma-data). Preserving ollama-data."
    dim "Use --clean-all to also remove the Ollama model cache."
    # Remove only the built service images (not the base images like mysql, ollama)
    $COMPOSE down --rmi local 2>/dev/null || true
    # Remove app data volumes by pattern — names vary by compose project prefix
    podman volume ls --format "{{.Name}}" 2>/dev/null \
      | grep -E "(mysql-data|chroma-data)" \
      | xargs -r podman volume rm 2>/dev/null || true
  fi
  info "Clean complete."
fi

# ── stage 1: start MySQL and Ollama (heavy services that need a head-start) ───
step "Stage 1/3 — Starting MySQL and Ollama"
$COMPOSE up -d mysql ollama
dim "Giving MySQL 20 s to initialise its data directory on first boot..."
sleep 20

wait_for_url "http://localhost:$OLLAMA_PORT/api/version" "Ollama :$OLLAMA_PORT" 120

# ── pull Ollama models if not already present ─────────────────────────────────
if ! $SKIP_MODELS; then
  step "Checking Ollama model cache"
  TAGS=$(curl -sf "http://localhost:$OLLAMA_PORT/api/tags" 2>/dev/null || echo "{}")

  GEN_KEY="${GEN_MODEL%%:*}"   # e.g. "llama3.2" from "llama3.2:3b"
  EMB_KEY="${EMB_MODEL%%:*}"   # e.g. "nomic-embed-text"

  if echo "$TAGS" | grep -q "$GEN_KEY"; then
    info "Generation model ($GEN_MODEL) already present — skipping pull."
  else
    warn "Generation model ($GEN_MODEL) not found. Pulling now (may take several minutes)..."
    $COMPOSE exec -T ollama ollama pull "$GEN_MODEL"
    info "Generation model ready."
  fi

  if echo "$TAGS" | grep -q "$EMB_KEY"; then
    info "Embedding model ($EMB_MODEL) already present — skipping pull."
  else
    warn "Embedding model ($EMB_MODEL) not found. Pulling now..."
    $COMPOSE exec -T ollama ollama pull "$EMB_MODEL"
    info "Embedding model ready."
  fi
fi

# ── stage 2: build and start all remaining services ───────────────────────────
step "Stage 2/3 — Building and starting remaining services"
dim "Spring Boot rebuilds from source on code changes. First build ~60 s."
$COMPOSE up --build -d

# ── stage 3: wait for Python AI service and Spring Boot ───────────────────────
step "Stage 3/3 — Waiting for services to become healthy"
wait_for_url "http://localhost:$AI_PORT/health"               "Python AI service :$AI_PORT" 120
wait_for_url "http://localhost:$SPRING_PORT/actuator/health"  "Spring Boot :$SPRING_PORT"   180

# ── final status ──────────────────────────────────────────────────────────────
step "All services are up"
$COMPOSE ps

echo ""
echo -e "${BOLD}  Endpoints${NC}"
echo -e "  ├─ Spring Boot API   ${GREEN}http://localhost:$SPRING_PORT${NC}"
echo -e "  ├─ Python AI service ${GREEN}http://localhost:$AI_PORT${NC}"
echo -e "  ├─ Adminer (MySQL)   ${GREEN}http://localhost:$ADMINER_PORT${NC}"
echo -e "  └─ Ollama LLM        ${GREEN}http://localhost:$OLLAMA_PORT${NC}"
echo ""
echo -e "  ${BOLD}Next steps${NC}"
echo -e "  1. Load demo users and records:"
echo -e "     ${DIM}./tools/scripts/seed-data.sh${NC}"
echo -e "  2. Install the Android APK on an emulator:"
echo -e "     ${DIM}WELLNESS_API_BASE_URL=http://10.0.2.2:$SPRING_PORT/ \\"
echo -e "       ./android-app/gradlew -p android-app :app:installDebug${NC}"
echo ""
