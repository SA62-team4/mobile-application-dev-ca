#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android-app"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle-cache}"
GRADLE_CMD="${GRADLE_CMD:-$ANDROID_DIR/gradlew}"

if [[ -z "${JAVA_HOME:-}" && -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" ]]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required. Install Android platform tools and enable USB debugging." >&2
  exit 1
fi

device_count="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"

if [[ "$device_count" -eq 0 ]]; then
  echo "No authorized Android device found. Connect a phone, enable USB debugging, and approve the prompt." >&2
  adb devices >&2
  exit 1
fi

if [[ "$device_count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
  echo "Multiple Android devices found. Set ANDROID_SERIAL to choose one." >&2
  adb devices >&2
  exit 1
fi

export WELLNESS_API_BASE_URL="${WELLNESS_API_BASE_URL:-http://127.0.0.1:8080/}"

if [[ "$WELLNESS_API_BASE_URL" != */ ]]; then
  echo "WELLNESS_API_BASE_URL must end with '/'." >&2
  exit 1
fi

adb reverse tcp:8080 tcp:8080
"$GRADLE_CMD" --gradle-user-home "$GRADLE_USER_HOME" -p "$ANDROID_DIR" :app:installDebug

echo "Installed debug app with API base URL: $WELLNESS_API_BASE_URL"
echo "Keep Docker backend running on the laptop at http://localhost:8080/"
