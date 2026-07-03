# Android Client

The Android app is the user-facing client. It uses Kotlin with XML layouts, not Compose.

## Responsibilities

The Android app handles:

- launch and login flows
- registration and Google Sign-In UI
- JWT storage and authenticated API calls
- wellness dashboard, record CRUD, chatbot, recommendations, and profile screens
- displaying loading, empty, success, and error states

The Android UI spec is in `docs/specs/07-plan-android-ui-flows.md`.

## Navigation model

The authenticated area uses bottom navigation with these main tabs:

- Dashboard
- Chat
- Recommendations
- Profile

The dashboard is the authenticated landing view and contains summary metrics plus the historical records list.

## Integration rules

Android should call only the Spring Boot backend. It must not connect directly to MySQL or to the Python AI service.

That boundary matters because:

- auth and authorization live in the backend
- the backend keeps user-scoped rules consistent
- AI calls need backend orchestration and persistence

## Google SSO notes

Google SSO is an additional login path, not a replacement for email/password login. The implementation guide in `docs/google-sso-setup.md` explains the Google Cloud setup, client IDs, shared debug keystore, and backend exchange flow.

Recent commits show the team had to fix two common failure modes:

- launch crashes when the Google Web Client ID was unset
- sign-in failures when debug signing certificates differed across machines

If you touch SSO-related Android code, verify the debug signing and token exchange assumptions together with the backend guide.

## Change guidance

When working on Android:

1. Update the UI spec first if screens, states, or navigation change.
2. Keep API usage aligned with the backend contract.
3. Check token storage and auth behavior carefully.
4. Re-run the relevant Android tests or build tasks after changes.

## Best starting points

- `docs/specs/07-plan-android-ui-flows.md`
- `docs/google-sso-setup.md`
- `android-app/`
