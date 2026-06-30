# Setup Guide

## Prerequisites

- Docker Desktop
- Java 17
- Maven
- .NET 10 SDK for optional backup backend and optional desktop client work
- Android Studio
- Gradle CLI
- Android platform tools (`adb`)
- PlantUML preview extension for diagrams

Local tool versions verified on this machine:

```bash
gradle --version
adb version
```

## Backend, Database, AI Service

```bash
cp .env.example .env
docker compose up -d mysql ollama
docker compose exec ollama ollama pull llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
docker compose up --build
```

Useful URLs:

- Spring Boot API: `http://localhost:8080`
- Optional .NET backup API: `http://localhost:8082`
- Python AI service: `http://localhost:8000/health`
- Ollama: `http://localhost:11434`
- Adminer: `http://localhost:8081`

Default host ports are configurable in `.env`. MySQL uses host port `3307` by default to avoid colliding with local MySQL installations, while Docker services still connect internally to `mysql:3306`.

```text
MYSQL_HOST_PORT=3307
SPRING_HOST_PORT=8080
DOTNET_BACKEND_HOST_PORT=8082
AI_SERVICE_HOST_PORT=8000
OLLAMA_HOST_PORT=11434
ADMINER_HOST_PORT=8081
```

## Optional .NET Backup Backend

The Java Spring Boot backend remains the required and canonical backend for the CA. The .NET backend is a cold-standby mirror for backup rehearsal only.

Run backup mode with the Compose override:

```bash
docker compose -f docker-compose.yml -f docker-compose.dotnet-backup.yml up --build dotnet-backend python-ai-service mysql ollama
```

Smoke-test either backend with the same contract script:

```bash
BASE_URL=http://localhost:8080 tools/scripts/backend-contract-smoke.sh
BASE_URL=http://localhost:8082 tools/scripts/backend-contract-smoke.sh
```

For Android backup rehearsal, point the app to `http://10.0.2.2:8082/` on emulator. For a physical device, reverse the backup port first:

```bash
adb reverse tcp:8082 tcp:8082
```

Adminer login for the Docker database:

```text
System: MySQL
Server: mysql
Username: wellness_user
Password: value of MYSQL_PASSWORD
Database: wellness_app
```

## Optional .NET Desktop Client

An optional cross-platform .NET (Avalonia) desktop client in `desktop-app/` is bonus evidence (`REQ-21`). It is an additional REST client only and calls the same Spring Boot API as Android.

Run it against a running Spring Boot backend on port `8080`:

```bash
cd desktop-app
dotnet run --project src/WellnessDesktop
```

Configuration:

- Backend base URL defaults to `http://localhost:8080/`.
- Override via `desktop-app/src/WellnessDesktop/appsettings.json` (`BackendBaseUrl`) or the `WELLNESS_API_BASE_URL` environment variable (e.g. `http://localhost:8082/` to target the .NET backup backend).

The desktop client stores the JWT in memory only; it is cleared on logout and never written to disk.

## Android

Open `android-app/` in Android Studio.

For CLI builds, use JDK 17 explicitly. Homebrew Gradle may launch with a newer JDK, which can break Android Gradle Plugin transforms.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:assembleDebug
./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:testDebugUnitTest
adb devices
```

### Choose Android Backend

The Android app reads its backend URL from `WELLNESS_API_BASE_URL` at build/install time. The value must end with `/` because Retrofit requires a trailing slash.

Use Spring Boot for the required CA demo:

| Device type | Android backend URL | Backend command |
| --- | --- | --- |
| Emulator | `http://10.0.2.2:8080/` | `docker compose up --build` |
| Physical phone | `http://127.0.0.1:8080/` | `docker compose up --build` plus `adb reverse tcp:8080 tcp:8080` |

Use optional .NET backup only for backup rehearsal:

| Device type | Android backend URL | Backend command |
| --- | --- | --- |
| Emulator | `http://10.0.2.2:8082/` | `docker compose -f docker-compose.yml -f docker-compose.dotnet-backup.yml up --build dotnet-backend python-ai-service mysql ollama adminer` |
| Physical phone | `http://127.0.0.1:8082/` | Same backup command plus `adb reverse tcp:8082 tcp:8082` |

Spring Boot remains the canonical backend for assignment evidence. The .NET backend mirrors the same API on port `8082` for cold-standby testing.

### DigitalOcean Backend Install Examples

Use these commands when the backend is deployed to the DigitalOcean Droplet and
DuckDNS points to the reserved IP. No `adb reverse` is needed because the app
connects over public HTTPS.

Check the deployed backend first:

```bash
curl https://sa62wellness.duckdns.org/actuator/health
```

Install the Android app for either emulator or physical phone:

```bash
WELLNESS_API_BASE_URL=https://sa62wellness.duckdns.org/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug
```

If multiple Android devices are connected, pick one explicitly:

```bash
adb devices
ANDROID_SERIAL=<device_serial> WELLNESS_API_BASE_URL=https://sa62wellness.duckdns.org/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug
```

Run the optional desktop client against the same deployed backend:

```bash
cd desktop-app
WELLNESS_API_BASE_URL=https://sa62wellness.duckdns.org/ dotnet run --project src/WellnessDesktop/WellnessDesktop.csproj
```

### Emulator Install Examples

Spring backend on `8080`:

```bash
WELLNESS_API_BASE_URL=http://10.0.2.2:8080/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug
```

.NET backup backend on `8082`:

```bash
WELLNESS_API_BASE_URL=http://10.0.2.2:8082/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug
```

### Physical Phone Install Examples

For repeatable phone demo/testing, run `tools/scripts/android-phone-demo.sh`. It installs a debug build and automatically runs `adb reverse` for the port in `WELLNESS_API_BASE_URL`.

Spring backend on `8080`:

```bash
WELLNESS_API_BASE_URL=http://127.0.0.1:8080/ tools/scripts/android-phone-demo.sh
```

.NET backup backend on `8082`:

```bash
WELLNESS_API_BASE_URL=http://127.0.0.1:8082/ tools/scripts/android-phone-demo.sh
```

Manual physical-phone commands are also valid:

```bash
adb reverse tcp:8080 tcp:8080
WELLNESS_API_BASE_URL=http://127.0.0.1:8080/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug

adb reverse tcp:8082 tcp:8082
WELLNESS_API_BASE_URL=http://127.0.0.1:8082/ ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:installDebug
```

Default backend URL if no override is provided:

- Emulator: `http://10.0.2.2:8080/`
- Physical device over USB: `http://127.0.0.1:8080/` with `adb reverse tcp:8080 tcp:8080`

## Validation

```bash
plantuml -checkonly docs/specs/*.md
cd spring-backend && mvn test
cd python-ai-service && python3 -m compileall app
dotnet test dotnet-backend/tests/Wellness.Backup.Api.Tests/Wellness.Backup.Api.Tests.csproj
dotnet test desktop-app/tests/WellnessDesktop.Tests/WellnessDesktop.Tests.csproj
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:assembleDebug
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./android-app/gradlew --gradle-user-home .gradle-cache -p android-app :app:testDebugUnitTest
docker compose config
```
