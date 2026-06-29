# 04 API Spec

## Spec Metadata

| Field | Value |
| --- | --- |
| Status | Draft baseline |
| Controls | REQ-02 through REQ-13, NFR-01, NFR-02 |
| Primary audience | Backend, Android, Python AI service, test owners |
| Upstream specs | `04-plan-system-architecture.md`, `05-plan-backend-data-model-erd.md` |
| Downstream specs | Android implementation, backend implementation, Python service clients, tests |

## API Principles

- REST clients (Android, and the optional .NET desktop client) call only the Spring Boot API.
- Java Spring Boot is the canonical backend API for assignment evidence. The optional `.NET Backup API` may expose the same routes for cold-standby rehearsal, but it must not define divergent behavior.
- All non-auth endpoints require `Authorization: Bearer <jwt>`.
- Public status endpoints may be exposed for local browser and health checks.
- Backend derives the current user from JWT claims, not from client-provided user ids.
- JSON is the request and response format.
- Error responses use one consistent shape.

## Optional .NET Backup Parity

If the `.NET Backup API` is implemented, it must preserve Spring Boot parity:

- Same route paths, HTTP methods, request fields, response fields, status codes, and camelCase JSON names.
- Same MySQL table and column ownership rules defined in `05-plan-backend-data-model-erd.md`.
- Same JWT secret, expiry setting, HS256 signing, bearer-token rules, and claims: `sub`, `uid`, `name`, `iat`, `exp`.
- BCrypt password hashes must be compatible with Spring Security so either backend can authenticate users stored by the other.
- Internal Python callbacks must use `X-Internal-Service-Token` and the same internal endpoint request/response shapes.
- Spring remains the source of truth when a contract ambiguity appears.

## Public Status Endpoints

### Backend Status

`GET /`

Response `200 OK`:

```json
{
  "service": "wellness-backend",
  "status": "UP",
  "health": "/actuator/health"
}
```

This endpoint is intentionally public for local development checks. It must not expose secrets, user data, stack traces, or database details.

### Health

`GET /actuator/health`

Response `200 OK`:

```json
{
  "status": "UP"
}
```

## Error Response

```json
{
  "timestamp": "2026-07-01T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Mood score must be between 1 and 5",
  "path": "/api/wellness-records"
}
```

## Auth Endpoints

### Register

`POST /api/auth/register`

Request:

```json
{
  "displayName": "Asha Tan",
  "email": "asha@example.com",
  "password": "Password123!"
}
```

Response `201 Created`:

```json
{
  "id": 1,
  "displayName": "Asha Tan",
  "email": "asha@example.com"
}
```

Validation:

- Email is required and unique.
- Password is required and should be at least 8 characters.
- Display name is required.

### Login

`POST /api/auth/login`

Request:

```json
{
  "email": "asha@example.com",
  "password": "Password123!"
}
```

Response `200 OK`:

```json
{
  "token": "jwt-token",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": {
    "id": 1,
    "displayName": "Asha Tan",
    "email": "asha@example.com"
  }
}
```

### Logout

`POST /api/auth/logout`

Response `204 No Content`.

Logout is stateless. The Android app clears the stored JWT after this call succeeds or if the user chooses local logout.

## Wellness Record Endpoints

### Create Record

`POST /api/wellness-records`

Request:

```json
{
  "recordDate": "2026-07-01",
  "sleepHours": 7.5,
  "exerciseType": "Walking",
  "exerciseMinutes": 30,
  "moodScore": 4,
  "notes": "Felt more energetic after dinner walk."
}
```

Response `201 Created`:

```json
{
  "id": 10,
  "recordDate": "2026-07-01",
  "sleepHours": 7.5,
  "exerciseType": "Walking",
  "exerciseMinutes": 30,
  "moodScore": 4,
  "notes": "Felt more energetic after dinner walk.",
  "createdAt": "2026-07-01T12:00:00Z",
  "updatedAt": "2026-07-01T12:00:00Z"
}
```

### List Records

`GET /api/wellness-records?from=2026-06-01&to=2026-07-01`

Response `200 OK`:

```json
[
  {
    "id": 10,
    "recordDate": "2026-07-01",
    "sleepHours": 7.5,
    "exerciseType": "Walking",
    "exerciseMinutes": 30,
    "moodScore": 4,
    "notes": "Felt more energetic after dinner walk.",
    "createdAt": "2026-07-01T12:00:00Z",
    "updatedAt": "2026-07-01T12:00:00Z"
  }
]
```

### Get Record

`GET /api/wellness-records/{id}`

Returns `404 Not Found` if the record does not exist for the authenticated user.

### Update Record

`PUT /api/wellness-records/{id}`

Uses the same request shape as create. Returns the updated record.

### Delete Record

`DELETE /api/wellness-records/{id}`

Response `204 No Content`.

## Chatbot Endpoints

### Ask Chatbot

`POST /api/chat/messages`

Request:

```json
{
  "question": "How can I improve my sleep if I exercise in the evening?"
}
```

Response `200 OK`:

```json
{
  "id": 25,
  "question": "How can I improve my sleep if I exercise in the evening?",
  "answer": "Try keeping evening exercise moderate and leave time to wind down before bed.",
  "sources": [
    {
      "title": "Sleep Hygiene Basics",
      "snippet": "A calming routine and consistent bedtime support better sleep quality."
    }
  ],
  "modelName": "llama3.2:3b",
  "createdAt": "2026-07-01T12:10:00Z"
}
```

Behavior:

- Backend forwards the question and recent wellness context to Python.
- Python retrieves relevant KB chunks and calls Ollama.
- Backend saves the final question, answer, source summary, and model name.

### List Chat History

`GET /api/chat/messages`

Returns chat messages for the authenticated user, newest first.

## Recommendation Endpoints

### Generate Recommendation

`POST /api/recommendations/generate`

Response `201 Created`:

```json
{
  "id": 8,
  "title": "Improve sleep consistency",
  "trendSummary": "Your sleep has varied between 5.5 and 8 hours over the last week.",
  "recommendationText": "Aim for a consistent bedtime and keep evening exercise light on days when sleep was below 6 hours.",
  "actionItems": [
    "Set a fixed bedtime for the next three nights",
    "Take a 20 minute walk before 8pm",
    "Avoid caffeine after lunch"
  ],
  "generatedBy": "python-agent",
  "createdAt": "2026-07-01T12:20:00Z"
}
```

### List Recommendations

`GET /api/recommendations`

Returns recommendations for the authenticated user, newest first.

## Internal Backend Endpoints

Internal endpoints are called by the Python AI service only. They require an internal service token, not a user JWT.

- `GET /api/internal/users/{userId}/wellness-records?days=14`
- `POST /api/internal/users/{userId}/recommendations`

These endpoints must not be exposed to Android.

## Python AI Service Endpoints

These endpoints are called by Spring Boot.

### RAG Chat

`POST /rag/chat`

Request:

```json
{
  "userId": 1,
  "question": "How can I improve my sleep?",
  "recentRecords": [
    {
      "recordDate": "2026-07-01",
      "sleepHours": 6,
      "exerciseType": "Running",
      "exerciseMinutes": 40,
      "moodScore": 3
    }
  ]
}
```

Response:

```json
{
  "answer": "A consistent bedtime and a calming routine may help improve sleep.",
  "sources": [
    {
      "title": "Sleep Hygiene Basics",
      "snippet": "Consistent sleep schedules support sleep quality."
    }
  ],
  "modelName": "llama3.2:3b"
}
```

### Agent Recommendation

`POST /agent/recommendation/{userId}`

Response uses the recommendation response shape after saving through Spring Boot.

### Reindex Knowledge Base

`POST /rag/reindex`

Development-only endpoint to rebuild the local vector index from curated KB files.
