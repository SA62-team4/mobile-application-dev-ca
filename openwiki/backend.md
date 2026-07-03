# Backend and API

The Spring Boot backend is the canonical server-side implementation. It owns authentication, authorization, business rules, and MySQL writes.

## What the backend does

From the specs, the backend is responsible for:

- registering and logging in users
- issuing JWTs
- enforcing user-scoped access to wellness data
- persisting wellness records, chat history, and recommendations
- orchestrating calls to the Python AI service
- exposing health and status endpoints for local and deployment checks

The core contract is documented in `docs/specs/05-plan-backend-data-model-erd.md` and `docs/specs/06-plan-api-contracts.md`.

## Data model at a glance

The planned MySQL model has four main tables:

- `users`
- `wellness_records`
- `chat_messages`
- `recommendations`

The ownership rule is important: all user-facing data must be filtered by the authenticated user id. The backend must not allow cross-user access by guessing ids.

## Auth and login paths

The backend supports JWT-based auth and a Google SSO login path in addition to the local email/password flow.

Useful references:

- `docs/specs/06-plan-api-contracts.md`
- `docs/google-sso-setup.md`
- `spring-backend/src/main/resources/application.yml`
- `spring-backend/src/main/java/sg/edu/nus/iss/wellness/config/SecurityConfig.java`

Recent git history shows Google SSO was hardened several times to avoid empty-client-id crashes and 401s when the backend or Android defaults were missing. That makes the Google client ID handling a sensitive integration point.

## API shape

The public contract includes:

- `GET /` for backend status
- `GET /actuator/health` for health checks
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google`
- authenticated wellness, chat, and recommendation endpoints defined in the API spec

All non-auth endpoints require `Authorization: Bearer <jwt>`.

## AI integration

The backend is the bridge between Android and the Python AI service. Android should never talk to Python directly. The backend receives authenticated requests, applies business rules, and then orchestrates the AI service for chat or recommendation workflows.

## Change guidance

If you modify backend behavior:

1. Update the matching spec first.
2. Check the API contract and the data model together.
3. Review the security config and recent Google SSO changes if auth is involved.
4. Run backend tests and any relevant contract or integration checks.

## Best starting points

- `docs/specs/05-plan-backend-data-model-erd.md`
- `docs/specs/06-plan-api-contracts.md`
- `docs/google-sso-setup.md`
- `spring-backend/`
