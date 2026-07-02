# Google SSO Integration — Full Implementation Guide

**Author:** Surya Kumaraguru  
**Branch:** `feature/google-sso`  
**Project:** wellness-app-500913 (Google Cloud)  
**Date:** 2026-06-29

This document covers the complete end-to-end implementation of Google Single Sign-On (SSO) across the Spring Boot backend, Android client, and Docker infrastructure. The existing email/password login and JWT flow are fully preserved — Google SSO is an additional login path only.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Google Cloud Console Setup](#2-google-cloud-console-setup)
3. [Backend Changes — Spring Boot](#3-backend-changes--spring-boot)
4. [Bug Fixes Discovered During Local Testing](#4-bug-fixes-discovered-during-local-testing)
5. [Android Client Changes](#5-android-client-changes)
6. [Docker & Environment Setup](#6-docker--environment-setup)
7. [Local Credentials Configuration](#7-local-credentials-configuration)
8. [API Reference](#8-api-reference)
9. [Testing](#9-testing)
10. [Git Branch & Pending Commit](#10-git-branch--pending-commit)
11. [Security Notes](#11-security-notes)

---

## 1. Architecture Overview

```
Android App
    │
    │  1. User taps "Sign in with Google"
    │  2. Google Sign-In SDK (play-services-auth) opens Google account picker
    │  3. SDK returns a Google ID Token (signed JWT)
    │
    ▼
POST /api/auth/google  { "idToken": "<google-id-token>" }
    │
    │  Spring Boot Backend
    │  4. GoogleTokenVerifier fetches Google's JWKS and verifies token signature
    │  5. Validates audience (our Web Client ID) and issuer (accounts.google.com)
    │  6. Extracts email and display name from token claims
    │  7. Looks up user by email in MySQL/H2 database
    │       ├── Found  → use existing account (works for email/password users too)
    │       └── New    → auto-provisions AppUser with Google display name, null password
    │  8. Issues internal HMAC-SHA JWT (same format as email/password login)
    │
    ▼
{ "token": "eyJ...", "tokenType": "Bearer", "expiresInSeconds": 86400, "user": {...} }
    │
    ▼
Android app saves JWT to SharedPreferences via TokenStore.
All subsequent API calls include: Authorization: Bearer <token>
```

**Key design decision:** The backend issues its own JWT after verifying the Google token. This means the Android app uses one consistent token format for all API calls, regardless of how the user logged in.

---

## 2. Google Cloud Console Setup

### 2.1 Project

- **Project name:** Wellness App  
- **Project ID:** `wellness-app-500913`

### 2.2 OAuth Consent Screen

- **User type:** External (Testing mode)
- **Scopes:** `email`, `profile`, `openid`
- **Test users:** Add any Google accounts that need to sign in during development

> While in Testing mode, only accounts listed under Test Users can sign in. To add another account: **APIs & Services → OAuth consent screen → Test users → + Add Users**.

### 2.3 OAuth 2.0 Credentials Created

| Type | Purpose | Client ID |
|------|---------|-----------|
| **Android** | Allows Google Sign-In SDK to authenticate on-device | `1018876301618-kfri3ask7i31km8bdop79df20uhb0b3d.apps.googleusercontent.com` |
| **Web application** | Used by Android `requestIdToken()` and validated by the backend | `1018876301618-1t9tsadf4skn80s4itkgs8b5u93lgfco.apps.googleusercontent.com` |

> **Important:** The Web application client ID is the one that matters for the backend and for `requestIdToken()`. The Android client ID is registered with the SHA-1 fingerprint and allows Google Play Services on the device to participate in the flow.

### 2.4 Debug Keystore SHA-1 Fingerprint

The project uses a **shared debug keystore** committed at `android-app/app/shared-debug.keystore`
(wired into `signingConfigs.debug` in `app/build.gradle`). Every developer's debug build is
therefore signed with the same certificate, so only **one** SHA-1 needs to be registered on the
Android OAuth client — this is what fixes Google Sign-In **error code 10 (`DEVELOPER_ERROR`)**
on machines other than the original author's.

Register this SHA-1 (package `sg.edu.nus.iss.wellness`) on the Android OAuth 2.0 client:

```
D9:45:D1:1B:81:72:62:4E:C4:70:DD:11:9D:93:0F:CF:39:44:8C:8E
```

Verify what your build actually signs with:
```bash
cd android-app && ./gradlew :app:signingReport   # look under Variant: debug
```

> The old SHA-1 `66:86:D6:3A:FE:64:32:83:8D:21:78:1D:1E:1D:B5:1C:2B:12:2E:63` was one machine's
> personal `~/.android/debug.keystore` and only worked on that machine. It can stay registered
> or be removed. The shared keystore uses the standard debug credentials
> (alias `androiddebugkey`, store/key password `android`) and is intentionally **not** a secret.
>
> For production, register the **release** keystore's SHA-1 (or the Play App Signing SHA-1) on a
> separate **Release** Android OAuth client ID.

---

## 3. Backend Changes — Spring Boot

All Java files are under `spring-backend/src/main/java/sg/edu/nus/iss/wellness/`.

### 3.1 `pom.xml` — New Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Brings in `spring-security-oauth2-jose` and the Nimbus JOSE+JWT library. Used by `GoogleTokenVerifier` to fetch Google's JWKS and verify token signatures without calling Google's tokeninfo endpoint on every request.

H2 scope also changed from `test` to `runtime` so the app can start locally without MySQL:

```xml
<!-- Before -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<!-- After -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

### 3.2 `src/main/resources/application.yml`

```yaml
app:
  google:
    client-id: ${GOOGLE_CLIENT_ID:}   # ← added; empty default = app starts without it
```

---

### 3.3 `config/AppProperties.java`

Added `Google` inner class bound to the `app.google` prefix:

```java
private Google google = new Google();

public Google getGoogle() { return google; }
public void setGoogle(Google google) { this.google = google; }

public static class Google {
    private String clientId;
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
}
```

---

### 3.4 `model/AppUser.java` — Nullable Password Hash

SSO users have no local password. The DB column constraint was relaxed:

```java
// Before
@Column(nullable = false, name = "password_hash")
private String passwordHash;

// After
@Column(name = "password_hash")
private String passwordHash;
```

`getPassword()` updated to guard against null (keeps `UserDetails` contract valid):

```java
@Override
public String getPassword() {
    return passwordHash != null ? passwordHash : "";
}
```

---

### 3.5 `dto/AuthDtos.java`

```java
public record GoogleAuthRequest(@NotBlank String idToken) {
}
```

---

### 3.6 `service/GoogleTokenVerifier.java` — New File

```java
/**
 * Validates Google ID tokens received from the Android client for SSO login.
 *
 * @author Surya Kumaraguru
 */
@Service
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private final JwtDecoder decoder;

    public GoogleTokenVerifier(AppProperties properties) {
        String clientId = properties.getGoogle().getClientId();
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder
                .withJwkSetUri(GOOGLE_JWKS_URI).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(clientId)
        );
        nimbusDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audienceValidator)
        );
        this.decoder = nimbusDecoder;
    }

    public GoogleUserInfo verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid Google ID token: " + e.getMessage(), e);
        }
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
        if (!"accounts.google.com".equals(issuer) && !"https://accounts.google.com".equals(issuer)) {
            throw new IllegalArgumentException("Invalid token issuer");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google token missing email claim");
        }
        return new GoogleUserInfo(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    public record GoogleUserInfo(String sub, String email, String name) {}
}
```

**Validations performed:**

| Check | Mechanism |
|-------|-----------|
| Signature | Nimbus fetches `googleapis.com/oauth2/v3/certs` and verifies cryptographically |
| Expiry | `JwtValidators.createDefault()` checks `exp` claim |
| Audience | Custom validator — `aud` must contain our Web Client ID |
| Issuer | Manual check — must be `accounts.google.com` |
| Email present | Guard against tokens issued without email scope |

---

### 3.7 `controller/AuthController.java` — New Endpoint

```java
@PostMapping("/google")
public AuthDtos.LoginResponse googleLogin(@Valid @RequestBody AuthDtos.GoogleAuthRequest request) {
    GoogleTokenVerifier.GoogleUserInfo info;
    try {
        info = googleTokenVerifier.verify(request.idToken());
    } catch (IllegalArgumentException e) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google token: " + e.getMessage());
    }
    String email = info.email().toLowerCase();
    AppUser user = users.findByEmail(email).orElseGet(() -> {
        AppUser newUser = new AppUser();
        newUser.setEmail(email);
        newUser.setDisplayName(info.name() != null && !info.name().isBlank()
                ? info.name().trim() : email);
        return users.save(newUser);
    });
    if (!user.isEnabled()) {
        throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled");
    }
    return new AuthDtos.LoginResponse(
            jwtService.generateToken(user), "Bearer",
            jwtService.expirySeconds(), DtoMapper.user(user)
    );
}
```

No change to `SecurityConfig` needed — `/api/auth/**` is already public.

---

## 4. Bug Fixes Discovered During Local Testing

Two pre-existing bugs were found and fixed while running the app locally for the first time (the H2 scope change made local startup possible and exposed them).

### 4.1 `security/JwtService.java` — Wrong Exception Type Caught

**Root cause:** JJWT 0.12.x changed `Decoders.BASE64.decode()` to throw `io.jsonwebtoken.io.DecodingException` (a `RuntimeException`) instead of `IllegalArgumentException`. The catch block only caught `IllegalArgumentException`, so any non-Base64 JWT secret (like the default `dev_secret_replace_with_very_long_random_value` which contains `_`) caused an unhandled exception, returning HTTP 500 on every login attempt.

```java
// Before — wrong exception type
} catch (IllegalArgumentException ignored) {
    keyBytes = secret.getBytes(StandardCharsets.UTF_8);
}

// After — catches all decode failures
} catch (Exception ignored) {
    keyBytes = secret.getBytes(StandardCharsets.UTF_8);
}
```

**Symptom:** `POST /api/auth/login` returned `500 Internal Server Error` with message `DecodingException: Illegal base64 character: '_'`.

### 4.2 H2 Driver Not Available at Runtime

**Root cause:** H2 was `<scope>test</scope>` in `pom.xml`, but `application.yml` defaults to an H2 in-memory URL. The driver wasn't on the classpath during `spring-boot:run`, causing startup failure.

**Fix:** Changed to `<scope>runtime</scope>`. H2 is still not used in Docker (MySQL is used there).

---

## 5. Android Client Changes

All files are under `android-app/app/src/main/`.

### 5.1 `app/build.gradle`

```groovy
// Reads GOOGLE_WEB_CLIENT_ID from local.properties (gitignored)
def localProps = new Properties()
def localPropsFile = rootProject.file('local.properties')
if (localPropsFile.exists()) localProps.load(localPropsFile.newDataInputStream())
def googleWebClientId = localProps.getProperty('GOOGLE_WEB_CLIENT_ID', '')

android {
    defaultConfig {
        // ...existing config...
        buildConfigField "String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId}\""
    }
}

dependencies {
    // ...existing dependencies...
    implementation "com.google.android.gms:play-services-auth:21.0.0"
}
```

---

### 5.2 `java/.../api/ApiModels.kt`

```kotlin
data class GoogleAuthRequest(val idToken: String)
```

---

### 5.3 `java/.../api/ApiService.kt`

```kotlin
@POST("api/auth/google")
suspend fun googleLogin(@Body request: GoogleAuthRequest): LoginResponse
```

---

### 5.4 `res/layout/activity_login.xml`

Added below the "Create account" button:

```xml
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    android:gravity="center"
    android:text="— or —"
    android:textColor="#6B7280" />

<Button
    android:id="@+id/googleSignInButton"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:text="Sign in with Google" />
```

---

### 5.5 `java/.../LoginActivity.kt` — Full Google Sign-In Flow

```kotlin
/**
 * Login screen — supports email/password and Google SSO.
 *
 * @author Surya Kumaraguru
 */
class LoginActivity : Activity() {

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }

    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        // ...existing setup...

        // Configure Google Sign-In with the Web Client ID
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Google Sign-In button
        findViewById<Button>(R.id.googleSignInButton).setOnClickListener {
            statusText.text = "Opening Google sign-in..."
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE_SIGN_IN) return
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken ?: run {
                statusText.text = "Google sign-in failed: no ID token returned."
                return
            }
            exchangeGoogleToken(idToken)
        } catch (e: ApiException) {
            statusText.text = "Google sign-in cancelled or failed (code ${e.statusCode})."
        }
    }

    private fun exchangeGoogleToken(idToken: String) {
        scope.launch {
            runCatching {
                statusText.text = "Signing in with Google..."
                ApiClient.create(tokenStore).googleLogin(GoogleAuthRequest(idToken))
            }.onSuccess { response ->
                onLoginSuccess(response.token, response.user.displayName, response.user.email)
            }.onFailure {
                statusText.text = "Google sign-in failed. Check backend connection and client ID."
            }
        }
    }

    private fun onLoginSuccess(token: String, displayName: String, email: String) {
        tokenStore.save(token, displayName, email)
        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
        finish()
    }
}
```

---

## 6. Docker & Environment Setup

### 6.1 `docker-compose.yml` — Added GOOGLE_CLIENT_ID

```yaml
spring-backend:
  environment:
    # ...existing vars...
    GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-}   # ← added
```

Docker Compose automatically reads `.env` from the project root, so setting `GOOGLE_CLIENT_ID` in `.env` is all that's needed.

### 6.2 Starting the Full Stack

```powershell
# From the project root (C:\Users\kumar\MobileCA\mobile-application-dev-ca)
docker compose up --build
```

Services started:

| Service | Port | Purpose |
|---------|------|---------|
| `mysql` | 3307 (host) | Persistent database |
| `spring-backend` | 8080 | REST API + SSO endpoint |
| `python-ai-service` | 8000 | RAG chatbot |
| `ollama` | 11434 | Local LLM |
| `adminer` | 8081 | DB admin UI |

The Spring Boot container will have `GOOGLE_CLIENT_ID` injected from the `.env` file.

---

## 7. Local Credentials Configuration

Both files are **gitignored** and must be set up on each developer machine.

### 7.1 `android-app/local.properties`

```properties
sdk.dir=C:\Users\kumar\AppData\Local\Android\Sdk
# Optional per-machine override of the Web OAuth 2.0 Client ID
GOOGLE_WEB_CLIENT_ID=1018876301618-1t9tsadf4skn80s4itkgs8b5u93lgfco.apps.googleusercontent.com
```

This value is baked into `BuildConfig.GOOGLE_WEB_CLIENT_ID` at compile time by Gradle.

> **Same for every device, not a secret.** `GOOGLE_WEB_CLIENT_ID` identifies the
> *backend/project*, not the phone — every build/device uses the identical value, and it
> is already embedded in the shipped APK. Because a build with an **empty** value crashes
> on launch (`requestIdToken("")` throws), a working default is committed in
> `android-app/gradle.properties`. Resolution order:
> `local.properties` > `GOOGLE_WEB_CLIENT_ID` env var > `gradle.properties` default.
> You only need `local.properties` for `sdk.dir`; overriding the client ID there is optional.
> See `android-app/local.properties.example`.

### 7.2 `.env` (project root)

```env
# Google SSO — Web OAuth 2.0 Client ID (used by backend to validate Google ID tokens)
GOOGLE_CLIENT_ID=1018876301618-1t9tsadf4skn80s4itkgs8b5u93lgfco.apps.googleusercontent.com
```

Docker Compose reads this file automatically.

> **Never commit either of these files.** Both are covered by `.gitignore`.

---

## 8. API Reference

### `POST /api/auth/google`

| Property | Value |
|----------|-------|
| Auth required | No |
| Content-Type | `application/json` |

**Request body:**
```json
{ "idToken": "<Google ID token from Android Google Sign-In SDK>" }
```

**Success `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": {
    "id": 1,
    "displayName": "Surya Kumaraguru",
    "email": "surya@gmail.com"
  }
}
```

**Error responses:**

| Status | Cause |
|--------|-------|
| `400` | `idToken` field missing or blank |
| `401` | Invalid signature, expired token, wrong audience/issuer |
| `403` | Account exists but is disabled |

---

## 9. Testing

### 9.1 Backend Endpoint Tests (Verified Locally)

Run against local server on port 8082 (`GOOGLE_CLIENT_ID=test-client-id`):

| Test | Command | Expected |
|------|---------|----------|
| Health | `GET /actuator/health` | `{"status":"UP"}` |
| Register | `POST /api/auth/register` | `201` with user object |
| Login | `POST /api/auth/login` | `200` with JWT |
| Google SSO — invalid token | `POST /api/auth/google {"idToken":"fake"}` | `401` |
| Google SSO — missing field | `POST /api/auth/google {}` | `400` |

All five tests passed.

### 9.2 Android Emulator Testing

**Prerequisites:**
1. Docker stack running (`docker compose up --build`)
2. Android Studio synced (File → Sync Project with Gradle Files)
3. `GOOGLE_WEB_CLIENT_ID` set in `local.properties`
4. Your Google account added as a test user in the OAuth consent screen

**Steps:**
1. Launch app in emulator (requires **Google APIs** system image, not plain AOSP)
2. Login screen shows: email/password fields + "— or —" divider + **Sign in with Google**
3. Tap **Sign in with Google** → Google account picker opens
4. Select your test account → backend provisions user and issues JWT
5. App navigates to HomeActivity — all features (records, chat, recommendations) work normally

### 9.3 Testing with Additional Google Accounts

Since the OAuth consent screen is in **Testing mode**, only whitelisted accounts can sign in:

1. Go to **Google Cloud Console → APIs & Services → OAuth consent screen**
2. Scroll to **Test users → + Add Users**
3. Enter the Gmail address → **Save**

The account can sign in immediately after being added.

---

## 10. Git Branch & Pending Commit

**Branch:** `feature/google-sso`

### Files changed (pending commit):

**Spring Boot backend:**

| File | Change |
|------|--------|
| `spring-backend/pom.xml` | Added `spring-boot-starter-oauth2-resource-server`; H2 scope → `runtime` |
| `spring-backend/src/main/resources/application.yml` | Added `app.google.client-id` |
| `config/AppProperties.java` | Added `Google` inner class |
| `model/AppUser.java` | `password_hash` nullable; `getPassword()` null guard |
| `dto/AuthDtos.java` | Added `GoogleAuthRequest` record |
| `service/GoogleTokenVerifier.java` | **New** — Google ID token verifier |
| `controller/AuthController.java` | Added `POST /api/auth/google` |
| `security/JwtService.java` | Fixed `DecodingException` catch (pre-existing bug) |

**Android app:**

| File | Change |
|------|--------|
| `android-app/app/build.gradle` | Added `play-services-auth`; `GOOGLE_WEB_CLIENT_ID` buildConfigField |
| `api/ApiModels.kt` | Added `GoogleAuthRequest` |
| `api/ApiService.kt` | Added `googleLogin()` |
| `res/layout/activity_login.xml` | Added "— or —" divider + Google Sign-In button |
| `LoginActivity.kt` | Full Google Sign-In flow with `onActivityResult` |

**Infrastructure:**

| File | Change |
|------|--------|
| `docker-compose.yml` | Added `GOOGLE_CLIENT_ID` env var to `spring-backend` service |
| `docs/google-sso-setup.md` | This document |

### Commit commands (after local testing is confirmed):

```bash
git add spring-backend/pom.xml \
        spring-backend/src/main/resources/application.yml \
        spring-backend/src/main/java/sg/edu/nus/iss/wellness/config/AppProperties.java \
        spring-backend/src/main/java/sg/edu/nus/iss/wellness/model/AppUser.java \
        spring-backend/src/main/java/sg/edu/nus/iss/wellness/dto/AuthDtos.java \
        spring-backend/src/main/java/sg/edu/nus/iss/wellness/service/GoogleTokenVerifier.java \
        spring-backend/src/main/java/sg/edu/nus/iss/wellness/controller/AuthController.java \
        spring-backend/src/main/java/sg/edu/nus/iss/wellness/security/JwtService.java \
        android-app/app/build.gradle \
        android-app/app/src/main/java/sg/edu/nus/iss/wellness/api/ApiModels.kt \
        android-app/app/src/main/java/sg/edu/nus/iss/wellness/api/ApiService.kt \
        android-app/app/src/main/res/layout/activity_login.xml \
        android-app/app/src/main/java/sg/edu/nus/iss/wellness/LoginActivity.kt \
        docker-compose.yml \
        docs/google-sso-setup.md

git commit -m "feat: add Google SSO to backend and Android client"
git push -u origin feature/google-sso
```

---

## 11. Security Notes

| Risk | Mitigation |
|------|-----------|
| Forged ID token | Signature verified against Google's JWKS (`/oauth2/v3/certs`) |
| Expired token | `JwtValidators.createDefault()` enforces `exp` claim |
| Token intended for another app | `aud` claim validated against our specific Web Client ID |
| Wrong issuer | Issuer checked — must be exactly `accounts.google.com` |
| SSO user using password login path | SSO users have `null` password hash; BCrypt `matches()` always returns false |
| Disabled accounts | `isEnabled()` checked before issuing any JWT |
| Secrets in source control | `GOOGLE_CLIENT_ID` in `.env` and `GOOGLE_WEB_CLIENT_ID` in `local.properties` — both gitignored |
| Client secret exposure | Web client secret is never used by the app; only the Client ID is needed for token validation |
