# Semicolon Take-Home — Auth, Org Profile, RBAC, Programs (minimal)

## What is in this repo
- `src/` — Java Spring Boot source (controllers, services, models, repositories)
- `tests/` — unit + integration tests (JUnit 5 + Mockito)
- `docs/openapi.yaml` — OpenAPI spec (generated / maintained)
- `postman/` — optional Postman collection (import to test endpoints)
- `README.md` — this document

---

## Quick start (Windows / Linux / macOS)

Requirements: JDK 17, Maven 3.8+

```bash
# build + tests
mvn clean verify

# run the app (dev)
mvn spring-boot:run
# OR run jar
java -jar target/<artifact-id>-<version>.jar
```

By default the app uses H2 (in-memory) for tests/dev. To view the H2 console: `http://localhost:8080/h2-console` (frames allowed by security config in dev).

Environment toggles you might want:
- `SIMULATE_EMAIL_BEHAVIOUR` — controls mock email sending: `ALWAYS_SUCCESS | ALWAYS_FAIL | RANDOM` (default `RANDOM`).
- `logo.validation.enabled` — toggle logo URL validation (true/false).

---

## Quick API summary (top endpoints)

### Auth
- `POST /v1/auth/signup` — register user (optionally create org if `organizationName` provided). Returns verification token in response (we do not send real email in this task).
- `POST /v1/auth/verify-email` — body: `{ "email": "..." }` and **Authorization: Bearer <token>** header (token returned during signup). Returns `successful | queued | failed` depending on outbox behavior.
- `POST /v1/auth/login` — body: `{ "email","password" }`. Returns `sessionId` (access token) and `refreshToken`.
- `POST /v1/auth/refresh-token` — body: `{ "refreshToken":"..." }`. Rotates refresh token and returns new `sessionId` + `refreshToken`.
- `POST /v1/auth/logout` — body: `{ "sessionId":"..." }` or Authorization: Bearer <sessionId>. Revokes session & refresh tokens (returns 204).

### Organization profile
- `GET /org/me` — authenticated. Returns org profile + `completenessPercent` and `missingFields`.
- `PATCH /org/update-org-profile` — authenticated, body: `{ "logoUrl","website","industry","description" }` — upsert fields.
- `PATCH /org/update-plan` — change org plan (Admin only, service validates limits on downgrade).

### Invites & RBAC
- `POST /v1/invites/admin/invite-member` — Admin-only endpoint. Body: `{ "email","role" }` returns invite token and outbox status. Uses mock outbox same as verify-email.
- `GET /v1/invites/view-invites` — authenticated user sees invites for their email.
- `POST /v1/invites/accept-invite` — authenticated user accepts invitation with `{ "token": "..." }`.

### Programs
- `POST /v1/programs` — create program (Admin/Manager). Body: `{ "name","description","startAt","endAt" }`.
- `PATCH /v1/programs/update-program/{programId}` — update program (Admin/Manager).
- `GET /v1/programs/show-program/{programId}` — get program details.
- `GET /v1/programs` — list active programs.
- `POST /v1/programs/archive-program` — archive by name (Admin/Manager).

### Health & Metrics
- `GET /.well-known/health` — readiness/liveness (public). Returns JSON with `status`, DB check and `appUptimeMs`.
- `GET /metrics` — text/plain Prometheus-like metrics (public): uptime, JVM memory, `db_up`.

---

## API docs + sample cURLs

### API docs (interactive)
When the app runs locally the interactive API docs (Swagger UI) are available at:

- Swagger UI: `http://localhost:8080/swagger-ui.html` (or `/swagger-ui/index.html`)
- Raw OpenAPI: `http://localhost:8080/v3/api-docs`
- Local copy: `docs/openapi.yaml`

If the UI does not appear, ensure `springdoc-openapi` starter dependency is on the classpath and the app is running.

---

### Example cURL commands (replace `localhost:8080` if running elsewhere)

#### Signup (returns verification token)
```bash
curl -X POST "http://localhost:8080/v1/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Passw0rd!","organizationName":"semicolon"}'
```
##### Example response
```json
{ "message":"Registration successful. Please verify your email.", "token":"<verification-token>" }
```

#### Verify Email (token in Authorization header, email in body)
```bash
curl -X POST "http://localhost:8080/v1/auth/verify-email" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <verification-token>" \
  -d '{"email":"alice@example.com"}'
```

#### Login (returns sessionId + refreshToken)
```bash
curl -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Passw0rd!"}'
```
##### Example response
```json
{
  "message": "Login successful",
  "sessionId": "<session-id>",
  "refreshToken": "<refresh-token>"
}
```

#### Refresh (rotate refresh token)
```bash
curl -X POST "http://localhost:8080/v1/auth/refresh-token" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh-token>"}'
```

##### Logout (revoke session)
```bash
curl -X POST "http://localhost:8080/v1/auth/logout" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<session-id>"}'
# OR with header
curl -X POST "http://localhost:8080/v1/auth/logout" -H "Authorization: Bearer <session-id>"
```
Response: `204 No Content`

#### Get org profile (authenticated)
```bash
curl -H "Authorization: Bearer <session-id>" http://localhost:8080/org/me
```

#### Update org profile (authenticated)
```bash
curl -X PATCH "http://localhost:8080/org/update-org-profile" \
  -H "Authorization: Bearer <session-id>" \
  -H "Content-Type: application/json" \
  -d '{"logoUrl":"https://example.com/logo.png","website":"https://example.com","industry":"EdTech","description":"We do X"}'
```

#### Create invite (Admin only)
```bash
curl -X POST "http://localhost:8080/v1/invites/admin/invite-member" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <session-id>" \
  -d '{"email":"bob@example.com","role":"MEMBER"}'
```

#### Accept invite (authenticated user)
```bash
curl -X POST "http://localhost:8080/v1/invites/accept-invite" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <session-session>" \
  -d '{"token":"<invite-token>"}'
```

#### Create program (Admin/Manager)
```bash
curl -X POST "http://localhost:8080/v1/programs" \
  -H "Authorization: Bearer <session-id>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Fall Cohort","description":"..."}'
```

#### Health
```bash
curl http://localhost:8080/.well-known/health
```
##### Response example
```json
{ "status":"ok", "checks": { "db":"ok" }, "details": { "appUptimeMs": 12345 } }
```

#### Metrics
```bash
curl http://localhost:8080/metrics
```
Returns Prometheus-style text metrics including `app_uptime_seconds`, `jvm_memory_used_bytes`, and `db_up`.

---

## How to run tests & view coverage
Unit tests: JUnit 5 + Mockito; integration tests run with Spring Boot (H2). Jacoco for coverage.

```bash
# run tests
mvn test
# full build + tests
mvn clean verify
# jacoco report
mvn jacoco:report
```

---

## Postman & OpenAPI
- Postman collection (if present): `postman/Semicolon.postman_collection.json` — import to Postman to try flows end-to-end.
- OpenAPI spec: `docs/openapi.yaml` — useful for generating clients or for interactive docs.

---

## Assumptions, trade-offs & notes (read before reviewing code)
- Access token is opaque: `sessionId` is a UUID stored in `auth_sessions` (not a JWT). Clients must send `Authorization: Bearer <sessionId>`.
- Verification tokens are returned in the signup response (we do not deliver real emails in this task). Verify endpoint expects the token in the `Authorization` header.
- Mock email outbox: the `OutboxWorker` simulates delivery and retries. Control behaviour with `SIMULATE_EMAIL_BEHAVIOUR` environment variable.
- Plan limits enforced atomically using DB row-lock (repository method `findByIdForUpdate`) or transactions — to avoid races.
- Error responses use a standard `ErrorEnvelope` shape. Validation errors are returned as structured `FieldDetail` objects.
- For simplicity the project uses a single-org-per-user model (no membership join table); migrating to many-orgs-per-user would require a Membership join table.

---


