# Local Setup Guide — Google SSO

<!-- @author Tiong Zhong Cheng, Kumaraguru Surya -->

This guide is for developers who have cloned the repo and want to run Google SSO locally. It assumes you already have the project running without SSO.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 17+ | [Download](https://www.oracle.com/java/technologies/downloads/) |
| Android Studio | Hedgehog+ | Includes JBR 21 needed for Gradle 9 |
| Docker Desktop | Any | Must be running before `docker compose up` |
| Maven | 3.x | Or use the spring-backend build directly |

---

## Step 1 — Get the OAuth Client IDs

Contact a team member for the credentials, or create your own in [Google Cloud Console](https://console.cloud.google.com) under project **wellness-app-500913**:

- **Web Client ID** — used by the backend to validate tokens and by Android's `requestIdToken()`
- **Android Client ID** — registers your debug SHA-1 fingerprint with Google Play Services

To get your debug SHA-1:
```bash
keytool -keystore ~/.android/debug.keystore \
        -list -v -alias androiddebugkey \
        -storepass android -keypass android
```

> If you're using the team's existing OAuth app, ask for the Web Client ID. You do NOT need your own Android client ID unless you change the signing key.

---

## Step 2 — Create `.env` (backend credentials)

Create a file named `.env` in the **project root** (same folder as `docker-compose.yml`):

```env
GOOGLE_CLIENT_ID=<paste-web-client-id-here>
```

> **This file is gitignored. Never commit it.**

---

## Step 3 — Create `local.properties` (Android credentials)

Open `android-app/local.properties` (create it if it doesn't exist) and add:

```properties
sdk.dir=C:\Users\<your-username>\AppData\Local\Android\Sdk
GOOGLE_WEB_CLIENT_ID=<paste-web-client-id-here>
```

Use the **same Web Client ID** as in `.env`.

> **This file is gitignored. Never commit it.**

---

## Step 4 — Run the Backend

### Option A — Docker (full stack with MySQL)

```bash
# From the project root
docker compose up --build
```

The backend will be available at `http://localhost:8080`.

> First run downloads the Ollama image (~4 GB). Subsequent runs use the cache.

### Option B — Maven only (H2 in-memory, no MySQL needed)

```bash
cd spring-backend
GOOGLE_CLIENT_ID=<your-web-client-id> mvn spring-boot:run
```

The backend starts on `http://localhost:8080`. H2 is used automatically when MySQL is not configured.

> Use Option B for quick backend-only testing. Use Option A for testing the full app.

---

## Step 5 — Verify the Backend is Working

```bash
# Health check
curl http://localhost:8080/actuator/health

# Should return:
# {"status":"UP"}

# Google endpoint — missing token (expect 400)
curl -s -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{}'

# Google endpoint — fake token (expect 401)
curl -s -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"not.a.real.token"}'
```

---

## Step 6 — Build and Run the Android App

### In Android Studio (recommended)

1. Open `android-app/` as the project root in Android Studio
2. **File → Sync Project with Gradle Files**
3. Select an emulator with **Google APIs** system image (not plain AOSP — Google Sign-In won't work without it)
4. Click **Run**

### From the command line

```bash
cd android-app

# Windows — use Android Studio's bundled JDK to avoid Gradle downloading JDK 21
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
./gradlew assembleDebug

# macOS/Linux
export JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home
./gradlew assembleDebug
```

Then install on a running emulator:
```bash
adb install -r app/build/intermediates/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n sg.edu.nus.iss.wellness/.LoginActivity
```

> The emulator connects to the host backend via `http://10.0.2.2:8080` (Android's alias for `localhost`).

---

## Step 7 — Add Your Google Account as a Test User

The OAuth consent screen is in **Testing mode**, so only whitelisted accounts can sign in.

1. Go to [Google Cloud Console](https://console.cloud.google.com) → **APIs & Services → OAuth consent screen**
2. Scroll to **Test users → + Add Users**
3. Enter your Gmail address → **Save**

Your account can sign in immediately after being added.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `GoogleSignIn` returns status code 10 | `GOOGLE_WEB_CLIENT_ID` in `local.properties` is wrong or the Android client ID wasn't registered with your debug SHA-1 |
| Backend returns 401 on `/api/auth/google` | `GOOGLE_CLIENT_ID` in `.env` doesn't match the Web Client ID used by the Android app |
| Backend returns 500 on login | Check that the Spring Boot started without errors. If using H2, confirm H2 scope is `runtime` in `pom.xml` |
| Gradle fails with "toolchain download" error | Run with `JAVA_HOME` pointing to Android Studio's JBR (see Step 6) |
| Google Sign-In button visible but nothing happens | The emulator may be using a plain AOSP image — switch to a **Google APIs** image |
| `INSTALL_FAILED_TEST_ONLY` when installing APK | Use `adb install -t` flag, or build with Android Studio which handles this automatically |
| Backend returns 500 on first Google login, log shows `Column 'password_hash' cannot be null` | Your MySQL volume was created before `password_hash` became nullable, and `ddl-auto: update` never relaxes an existing `NOT NULL` constraint. Fix without data loss: `docker compose exec mysql mysql -uwellness_user -p<password> wellness_app -e "ALTER TABLE users MODIFY password_hash VARCHAR(255) NULL;"` — or wipe the volume with `docker compose down -v && docker compose up --build`. |

---

## What's Gitignored (must set up per machine)

| File | Location | Contains |
|------|----------|---------|
| `.env` | project root | `GOOGLE_CLIENT_ID` for Docker |
| `local.properties` | `android-app/` | `GOOGLE_WEB_CLIENT_ID` for Android build |

These files are listed in `.gitignore` and will never be committed.
