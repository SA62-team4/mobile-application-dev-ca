# API Examples

<!-- @author Tiong Zhong Cheng -->

## Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"Demo User","email":"demo@example.com","password":"Password123!"}'
```

## Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"Password123!"}'
```

Store the returned token:

```bash
TOKEN='paste-token-here'
```

## Create Wellness Record

```bash
curl -X POST http://localhost:8080/api/wellness-records \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"recordDate":"2026-07-01","sleepHours":6.5,"exerciseType":"Walking","exerciseMinutes":30,"moodScore":4,"notes":"Evening walk helped."}'
```

## Ask Chatbot

```bash
curl -X POST http://localhost:8080/api/chat/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"How can I improve my sleep routine?"}'
```

## Generate Recommendation

```bash
curl -X POST http://localhost:8080/api/recommendations/generate \
  -H "Authorization: Bearer $TOKEN"
```

