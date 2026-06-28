# Wellness Desktop Client (.NET / Avalonia)

Optional cross-platform desktop client for the AI-Enabled Wellness app (bonus
requirement **REQ-21**). It is an additional REST client only — it consumes the
**same Spring Boot API** as the Android app (auth, wellness CRUD, RAG chatbot, AI
recommendations) and does not replace Android, the backend, or any mandatory
requirement.

Built with C#, Avalonia 11 (XAML), and the MVVM pattern. The JWT is held in memory
only and cleared on logout; the desktop talks only to the Spring Boot backend.

## Project layout

```text
desktop-app/
  WellnessDesktop.sln
  src/WellnessDesktop/
    Models/            DTOs mirroring docs/specs/06-plan-api-contracts.md
    Services/          ApiClient (REST), SessionStore (in-memory JWT)
    ViewModels/        Login, Records, Chat, Recommendations, Shell
    Views/             Avalonia .axaml screens
    appsettings.json   BackendBaseUrl
  tests/WellnessDesktop.Tests/   xUnit tests (ApiClient + DTO serialization)
  build-desktop.sh     Build self-contained Windows/macOS binaries
```

## Prerequisites

- [.NET 10 SDK](https://dotnet.microsoft.com/download)
- A running Spring Boot backend (default `http://localhost:8080/`). See the repo
  root `README.md` / `docs/setup.md` to start the backend stack.

## Run in development

```bash
cd desktop-app
dotnet run --project src/WellnessDesktop
```

## Configuration

The backend base URL is resolved in this order:

1. `WELLNESS_API_BASE_URL` environment variable
2. `BackendBaseUrl` in `src/WellnessDesktop/appsettings.json`
3. Default `http://localhost:8080/`

Examples:

```bash
# Point at the optional .NET backup backend on 8082
WELLNESS_API_BASE_URL=http://localhost:8082/ dotnet run --project src/WellnessDesktop
```

For a published build, edit the `appsettings.json` that sits next to the binary.

## Test

```bash
cd desktop-app
dotnet test WellnessDesktop.sln
```

## Build distributable executables

Use the helper script (run from `desktop-app/`). Builds are **self-contained and
single-file** — the .NET runtime is bundled, so target machines need nothing
installed. Cross-compiling works from any host (build the Windows `.exe` from a Mac).

```bash
./build-desktop.sh                 # all targets: win-x64, osx-arm64, osx-x64
./build-desktop.sh win-x64         # one or more specific targets
MAKE_APP=1 ./build-desktop.sh      # also wrap macOS builds into WellnessDesktop.app
```

Output goes to `artifacts/<rid>/` (git-ignored):

| Target | RID | Output |
| --- | --- | --- |
| Windows 64-bit | `win-x64` | `artifacts/win-x64/WellnessDesktop.exe` |
| macOS Apple Silicon | `osx-arm64` | `artifacts/osx-arm64/WellnessDesktop` (+ `.app` with `MAKE_APP=1`) |
| macOS Intel | `osx-x64` | `artifacts/osx-x64/WellnessDesktop` |

### Equivalent manual command

```bash
dotnet publish src/WellnessDesktop/WellnessDesktop.csproj -c Release \
  -r win-x64 --self-contained true -p:PublishSingleFile=true \
  -o artifacts/win-x64
```

Drop `--self-contained true -p:PublishSingleFile=true` for a smaller build that
requires the .NET 10 runtime to be pre-installed on the target machine.

### Running the builds

- **Windows:** double-click `WellnessDesktop.exe`. SmartScreen may warn because the
  binary is unsigned — choose *More info → Run anyway*.
- **macOS:** the binary is unsigned, so Gatekeeper quarantines it on first launch.
  Clear the quarantine flag, then open:

  ```bash
  xattr -cr artifacts/osx-arm64/WellnessDesktop          # or the .app
  chmod +x artifacts/osx-arm64/WellnessDesktop
  ./artifacts/osx-arm64/WellnessDesktop                  # or open WellnessDesktop.app
  ```

  Pick `osx-arm64` for Apple Silicon (M1/M2/M3) or `osx-x64` for Intel Macs.

## Specs

This client is tracked in the spec kit under `REQ-21`:
`docs/specs/02`, `04` (architecture), `06` (API contracts), `12` (tasks, Phase 8),
`13` (traceability), `14` (gates), `15` (test/demo plan).
