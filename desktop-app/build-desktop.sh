#!/usr/bin/env bash
# @author Tiong Zhong Cheng
#
# Produces standalone, single-file builds of the Avalonia desktop client for
# Windows and macOS. The .NET runtime is bundled, so target machines need
# nothing installed. Cross-compiling works from any host (e.g. build the
# Windows .exe from macOS).
#
# Usage:
#   ./build-desktop.sh                 # build all targets (win-x64, osx-arm64, osx-x64)
#   ./build-desktop.sh win-x64         # build one or more specific targets
#   MAKE_APP=1 ./build-desktop.sh      # also wrap macOS builds into .app bundles
#   BACKEND_BASE_URL=https://sa62wellness.duckdns.org/ ./build-desktop.sh osx-arm64
#
# Output: artifacts/<rid>/

set -euo pipefail

cd "$(dirname "$0")"

PROJECT="src/WellnessDesktop/WellnessDesktop.csproj"
DEFAULT_TARGETS=("win-x64" "osx-arm64" "osx-x64")
TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then
  TARGETS=("${DEFAULT_TARGETS[@]}")
fi

write_backend_config() {
  local rid="$1"
  local publish_dir="artifacts/${rid}"
  local base_url="${BACKEND_BASE_URL:-}"

  if [ -z "${base_url}" ]; then
    return
  fi

  if [[ "${base_url}" != */ ]]; then
    base_url="${base_url}/"
  fi

  local escaped="${base_url//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  printf '{\n  "BackendBaseUrl": "%s"\n}\n' "${escaped}" > "${publish_dir}/appsettings.json"
  echo "  Wrote BackendBaseUrl=${base_url} to ${publish_dir}/appsettings.json"
}

# Wrap a published macOS binary into a minimal double-clickable .app bundle.
make_app_bundle() {
  local rid="$1"
  local publish_dir="artifacts/${rid}"
  local app_dir="artifacts/${rid}/WellnessDesktop.app"
  echo "  Wrapping ${rid} into WellnessDesktop.app"
  rm -rf "${app_dir}"
  mkdir -p "${app_dir}/Contents/MacOS" "${app_dir}/Contents/Resources"

  cat > "${app_dir}/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Wellness Desktop</string>
  <key>CFBundleDisplayName</key><string>Wellness Desktop</string>
  <key>CFBundleIdentifier</key><string>sg.edu.nus.iss.wellness.desktop</string>
  <key>CFBundleVersion</key><string>1.0.0</string>
  <key>CFBundleShortVersionString</key><string>1.0.0</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleExecutable</key><string>WellnessDesktop</string>
  <key>LSMinimumSystemVersion</key><string>11.0</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST

  # Copy the published files into the bundle (the binary goes under MacOS/),
  # excluding the .app directory itself to avoid copying the bundle into itself.
  find "${publish_dir}" -maxdepth 1 -mindepth 1 ! -name 'WellnessDesktop.app' \
    -exec cp -R {} "${app_dir}/Contents/MacOS/" \;
  chmod +x "${app_dir}/Contents/MacOS/WellnessDesktop"
  echo "  Created ${app_dir}"
}

for rid in "${TARGETS[@]}"; do
  echo "==> Publishing ${rid}"
  rm -rf "artifacts/${rid}"
  dotnet publish "${PROJECT}" \
    -c Release \
    -r "${rid}" \
    --self-contained true \
    -p:PublishSingleFile=true \
    -o "artifacts/${rid}"

  write_backend_config "${rid}"

  case "${rid}" in
    osx-*)
      chmod +x "artifacts/${rid}/WellnessDesktop" || true
      if [ "${MAKE_APP:-0}" = "1" ]; then
        make_app_bundle "${rid}"
      fi
      ;;
  esac
  echo "    Done: artifacts/${rid}/"
done

echo "All requested targets built under desktop-app/artifacts/"
