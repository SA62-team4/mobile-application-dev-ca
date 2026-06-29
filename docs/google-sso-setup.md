# Google SSO Integration — Spring Boot Backend

This document covers every step taken to add Google Single Sign-On (SSO) to the Spring Boot backend of the Wellness application. The strategy keeps the existing email/password and JWT flows completely intact; Google SSO is an additional login path only.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Google Cloud Console Setup](#2-google-cloud-console-setup)
3. [Backend Changes](#3-backend-changes)
   - [3.1 pom.xml — New Dependency](#31-pomxml--new-dependency)
   - [3.2 application.yml — Google Client ID Config](#32-applicationyml--google-client-id-config)
   - [3.3 AppProperties.java — Google Config Class](#33-apppropertiesjava--google-config-class)
   - [3.4 AppUser.java — Nullable Password Hash](#34-appuserjava--nullable-password-hash)
   - [3.5 AuthDtos.java — GoogleAuthRequest Record](#35-authdtosjava--googleauthrequest-record)
   - [3.6 GoogleTokenVerifier.java — New Service](#36-googletokenveriferjava--new-service)
   - [3.7 AuthController.java — New Endpoint](#37-authcontrollerjava--new-endpoint)
4. [Environment Variable](#4-environment-variable)
5. [API Reference](#5-api-reference)
6. [Android Client Integration](#6-android-client-integration)
7. [Testing the Endpoint](#7-testing-the-endpoint)
8. [Git Branch & Commit](#8-git-branch--commit)
9. [Security Notes](#9-security-notes)

---

## 1. Architecture Overview

```
Android App
    │
    │  1. User taps "Sign in with Google"
    │  2. Google Sign-In SDK opens Google auth
    │  3. SDK returns a Google ID Token (JWT)
    │
    ▼
POST /api/auth/google   { "idToken": "<google-id-token>" }
    │
    │  Spring Boot Backend
    │  4. Validates ID token signature via Google JWKS
    │  5. Verifies audience (our Client ID) and issuer
    │  6. Looks up user by email in DB
    │     ├── Found → use existing account
    │     └── Not found → auto-provision new account
    │  7. Issues internal JWT (same format as email/password login)
    │
    ▼
{ "token": "...", "tokenType": "Bearer", "expiresInSeconds": 86400, "user": {...} }
    │
    ▼
Android App stores JWT and uses it for all subsequent API calls
```

The existing `POST /api/auth/login` (email + password) and JWT validation filter are **not modified**.

---

## 2. Google Cloud Console Setup

### 2.1 Create a Google Cloud Project (skip if you already have one)

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Click the project dropdown → **New Project**
3. Name it (e.g., `wellness-app`) → **Create**

### 2.2 Enable the Google Identity API

1. In the left menu: **APIs & Services → Library**
2. Search for **"Google Identity"** → select **Google Identity Toolkit API** → **Enable**

### 2.3 Create OAuth 2.0 Credentials

1. In the left menu: **APIs & Services → Credentials**
2. Click **+ Create Credentials → OAuth client ID**
3. If prompted, configure the **OAuth consent screen** first:
   - User Type: **External**
   - Fill in App name, support email, developer email → **Save and Continue**
   - Scopes: add `email` and `profile` → **Save and Continue**
   - Add your test Google accounts under **Test users** → **Save**
4. Back in **Create OAuth client ID**:
   - Application type: **Android**
   - Package name: your Android app's package (e.g., `sg.edu.nus.iss.wellness`)
   - SHA-1 certificate fingerprint — run this command in your Android project:
     ```bash
     # For debug keystore (development)
     keytool -keystore ~/.android/debug.keystore \
             -list -v -alias androiddebugkey \
             -storepass android -keypass android
     ```
   - Copy the **SHA1** value into the form → **Create**
5. Note down the **Client ID** (looks like `xxxx.apps.googleusercontent.com`)

> **Note:** You need one OAuth client ID per platform. If you later need a web client ID (for browser testing), create a separate **Web application** credential. The Android client ID is what the mobile app uses and what the backend must validate against.

---

## 3. Backend Changes

All files are under `spring-backend/src/main/java/sg/edu/nus/iss/wellness/` unless stated otherwise.

### 3.1 `pom.xml` — New Dependency

Added `spring-boot-starter-oauth2-resource-server`, which pulls in Spring Security's OAuth2 JWT libraries (Nimbus JOSE) needed to validate Google ID tokens against Google's public JWKS endpoint.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

**Why this dependency:** The `NimbusJwtDecoder` class it provides fetches Google's public keys automatically, verifies the token signature, and checks standard claims (expiry, not-before). This removes the need to manually call Google's tokeninfo endpoint on every request.

---

### 3.2 `src/main/resources/application.yml` — Google Client ID Config

```yaml
app:
  google:
    client-id: ${GOOGLE_CLIENT_ID:}   # ← added
```

The `GOOGLE_CLIENT_ID` environment variable holds the OAuth 2.0 Android client ID from Google Cloud Console. The empty default means the app will start without it (useful in local dev/test), but token validation will fail at runtime if a Google login is attempted.

---

### 3.3 `config/AppProperties.java` — Google Config Class

Added an inner `Google` class and wired it to the `app.google` prefix in YAML:

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

SSO users authenticated via Google have no local password. The `password_hash` column constraint was relaxed from `NOT NULL` to nullable:

```java
// Before
@Column(nullable = false, name = "password_hash")
private String passwordHash;

// After
@Column(name = "password_hash")
private String passwordHash;
```

`getPassword()` was also updated to return an empty string instead of `null` so Spring Security's `DaoAuthenticationProvider` does not throw a `NullPointerException` when loading these accounts:

```java
@Override
public String getPassword() {
    return passwordHash != null ? passwordHash : "";
}
```

> SSO users will never go through the password-login path, but this defensive return keeps the `UserDetails` contract valid.

---

### 3.5 `dto/AuthDtos.java` — GoogleAuthRequest Record

Added a new request DTO:

```java
public record GoogleAuthRequest(@NotBlank String idToken) {
}
```

The Android client sends the raw Google ID token string in this field.

---

### 3.6 `service/GoogleTokenVerifier.java` — New Service

**Full file:** `spring-backend/src/main/java/sg/edu/nus/iss/wellness/service/GoogleTokenVerifier.java`

```java
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
        Jwt jwt = decoder.decode(idToken);   // throws JwtException if invalid

        String issuer = jwt.getIssuer().toString();
        if (!"accounts.google.com".equals(issuer) &&
                !"https://accounts.google.com".equals(issuer)) {
            throw new IllegalArgumentException("Invalid token issuer");
        }

        String email = jwt.getClaimAsString("email");
        String name  = jwt.getClaimAsString("name");
        String sub   = jwt.getSubject();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google token missing email claim");
        }
        return new GoogleUserInfo(sub, email, name);
    }

    public record GoogleUserInfo(String sub, String email, String name) {}
}
```

**What it validates:**

| Check | How |
|-------|-----|
| Signature | Nimbus fetches Google's JWKS and verifies cryptographic signature |
| Expiry | `JwtValidators.createDefault()` checks `exp` claim |
| Audience | Custom validator checks `aud` contains our Client ID |
| Issuer | Manual check against `accounts.google.com` |
| Email present | Guards against tokens that omit the email scope |

---

### 3.7 `controller/AuthController.java` — New Endpoint

Injected `GoogleTokenVerifier` and added `POST /api/auth/google`:

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
            jwtService.generateToken(user),
            "Bearer",
            jwtService.expirySeconds(),
            DtoMapper.user(user)
    );
}
```

This endpoint is already public because `SecurityConfig` permits all `/api/auth/**` paths — no change to `SecurityConfig` was required.

**User provisioning logic:**

- If the Google email already exists in the DB → the existing account is used (works for users who registered with email/password using the same address).
- If the email is new → a new `AppUser` is created with the Google display name and no password hash.

---

## 4. Environment Variable

Set `GOOGLE_CLIENT_ID` to the **Android OAuth 2.0 Client ID** from Google Cloud Console before starting the backend:

```bash
# Linux / macOS / Docker
export GOOGLE_CLIENT_ID=123456789-xxxxxxxxxxxx.apps.googleusercontent.com

# Windows PowerShell
$env:GOOGLE_CLIENT_ID = "123456789-xxxxxxxxxxxx.apps.googleusercontent.com"

# docker-compose / docker run
-e GOOGLE_CLIENT_ID=123456789-xxxxxxxxxxxx.apps.googleusercontent.com
```

For local development without actually testing SSO, the app will start fine without this variable set; the endpoint will just return 401 for any token submitted.

---

## 5. API Reference

### `POST /api/auth/google`

Authenticate with a Google ID token and receive a backend JWT.

**Request**

```
POST /api/auth/google
Content-Type: application/json
```

```json
{
  "idToken": "<Google ID token from Android Google Sign-In SDK>"
}
```

**Success Response — `200 OK`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": {
    "id": 42,
    "displayName": "John Doe",
    "email": "john.doe@gmail.com"
  }
}
```

**Error Responses**

| Status | Reason |
|--------|--------|
| `400 Bad Request` | `idToken` field is missing or blank |
| `401 Unauthorized` | Token signature invalid, expired, wrong audience/issuer |
| `403 Forbidden` | User account is disabled in the database |

---

## 6. Android Client Integration

Add the Google Sign-In dependency in your Android `build.gradle`:

```groovy
implementation 'com.google.android.gms:play-services-auth:21.0.0'
```

Configure and trigger sign-in, then send the ID token to the backend:

```kotlin
// Configure Google Sign-In
val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestIdToken("YOUR_ANDROID_CLIENT_ID.apps.googleusercontent.com")
    .requestEmail()
    .build()
val googleSignInClient = GoogleSignIn.getClient(this, gso)

// Launch sign-in intent
startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)

// Handle result
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RC_SIGN_IN) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: return
            sendTokenToBackend(idToken)
        } catch (e: ApiException) {
            // handle sign-in failure
        }
    }
}

// Send token to your backend
fun sendTokenToBackend(idToken: String) {
    // POST { "idToken": idToken } to https://your-backend/api/auth/google
    // Store the returned JWT and use it for all subsequent API calls
}
```

> Replace `YOUR_ANDROID_CLIENT_ID` with the Client ID from Google Cloud Console.

---

## 7. Testing the Endpoint

### Option A — Obtain a real test token

1. Run the Android app against your backend (or use a browser flow with a **Web** OAuth client).
2. Sign in with Google.
3. Copy the ID token printed in Logcat or returned by the backend.
4. Replay it with curl within the token's 1-hour validity window.

### Option B — Use Google's token info endpoint to inspect a token

```bash
curl "https://oauth2.googleapis.com/tokeninfo?id_token=<YOUR_ID_TOKEN>"
```

### Option C — curl the endpoint directly

```bash
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"<google-id-token>"}'
```

Expected success response:
```json
{
  "token": "eyJ...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": { "id": 1, "displayName": "John Doe", "email": "john@gmail.com" }
}
```

---

## 8. Git Branch & Commit

All changes live on the `feature/google-sso` branch:

```bash
git checkout feature/google-sso
```

**Files changed in the commit:**

| File | Change |
|------|--------|
| `spring-backend/pom.xml` | Added `spring-boot-starter-oauth2-resource-server` dependency |
| `spring-backend/src/main/resources/application.yml` | Added `app.google.client-id` config key |
| `config/AppProperties.java` | Added `Google` inner class with `clientId` field |
| `model/AppUser.java` | Made `password_hash` column nullable; fixed `getPassword()` null guard |
| `dto/AuthDtos.java` | Added `GoogleAuthRequest` record |
| `service/GoogleTokenVerifier.java` | **New file** — verifies Google ID tokens |
| `controller/AuthController.java` | Injected `GoogleTokenVerifier`; added `POST /api/auth/google` |

To push the branch to GitHub:

```bash
git push -u origin feature/google-sso
```

To merge into `main` when ready:

```bash
git checkout main
git merge feature/google-sso
git push
```

---

## 9. Security Notes

| Risk | Mitigation |
|------|-----------|
| Forged tokens | Signature verified against Google's JWKS (`/oauth2/v3/certs`) |
| Token replay after expiry | `JwtValidators.createDefault()` checks `exp` claim |
| Token intended for another app | Audience (`aud`) claim validated against our Client ID |
| Phishing via wrong issuer | Issuer checked against `accounts.google.com` only |
| SSO user bypassing password login | SSO user has `null` password hash; BCrypt `matches()` returns false for any input |
| Disabled accounts | `isEnabled()` checked before issuing JWT |

> **Do not** commit the actual `GOOGLE_CLIENT_ID` value into source control. Keep it in environment variables or a secrets manager.
