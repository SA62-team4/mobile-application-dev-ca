# Setup Guide

## Prerequisites

- Docker Desktop
- Java 17
- Maven
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
- Python AI service: `http://localhost:8000/health`
- Ollama: `http://localhost:11434`
- Adminer: `http://localhost:8081`

Default host ports are configurable in `.env`. MySQL uses host port `3307` by default to avoid colliding with local MySQL installations, while Docker services still connect internally to `mysql:3306`.

```text
MYSQL_HOST_PORT=3307
SPRING_HOST_PORT=8080
AI_SERVICE_HOST_PORT=8000
OLLAMA_HOST_PORT=11434
ADMINER_HOST_PORT=8081
```

Adminer login for the Docker database:

```text
System: MySQL
Server: mysql
Username: wellness_user
Password: value of MYSQL_PASSWORD
Database: wellness_app
```

## Android

Open `android-app/` in Android Studio.

For CLI builds, use JDK 17 explicitly. Homebrew Gradle may launch with a newer JDK, which can break Android Gradle Plugin transforms.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
gradle --gradle-user-home .gradle-cache -p android-app :app:assembleDebug
gradle --gradle-user-home .gradle-cache -p android-app :app:testDebugUnitTest
adb devices
```

Default backend URL:

- Emulator: `http://10.0.2.2:8080/`
- Physical device over USB: `http://127.0.0.1:8080/` with `adb reverse tcp:8080 tcp:8080`

For repeatable phone demo/testing, keep the tracked Gradle default unchanged and run:

```bash
tools/scripts/android-phone-demo.sh
```

The script installs a debug build with `WELLNESS_API_BASE_URL=http://127.0.0.1:8080/`. For manual runs, pass either a Gradle property or an environment variable:

```bash
adb reverse tcp:8080 tcp:8080
WELLNESS_API_BASE_URL=http://127.0.0.1:8080/ gradle --gradle-user-home .gradle-cache -p android-app :app:installDebug
```

## Validation

```bash
plantuml -checkonly docs/specs/*.md
cd spring-backend && mvn test
cd python-ai-service && python3 -m compileall app
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home gradle --gradle-user-home .gradle-cache -p android-app :app:assembleDebug
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home gradle --gradle-user-home .gradle-cache -p android-app :app:testDebugUnitTest
docker compose config
```
