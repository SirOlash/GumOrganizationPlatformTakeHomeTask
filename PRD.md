# PRD — Gum Organizations Platform (Org / Auth / Programs)
## Summary (rewrite in my words)
This repo is a small SaaS-style backend for organizations: signup + email verification, org profile, RBAC & invites, programs (CRUD, filters), plans with limits (Free/Pro/Enterprise), and a mocked email outbox with retries. The goal is a minimally complete, testable API you can exercise in Postman: auth flows (signup → verify → login/refresh/logout), org profile upsert + completeness, invites & membership flows, program create/update/list/archive, plan enforcement, health/metrics, and a standard error envelope.

### Goals
Deliver a small, production-like REST API for orgs and programs.

Make plan limits enforceable and race-safe.

Provide deterministic mock email behavior (dev toggle: ALWAYS_SUCCESS, ALWAYS_FAIL, RANDOM).

Keep things minimal: sane defaults, clear acceptance criteria, and testable with Postman.

### Core Users
Organization Admin — creates org, invites, changes plan, manages members & programs.

Manager — can create & update programs, accept invites (when assigned Manager role).

Member — program consumer/member role.

System — background worker (OutboxWorker) and scheduled tasks.

### Glossary
Program — a named entity under an org (simple class/object). Programs can be Active or Archived.

OutboxEmail — DB row representing a queued/mock email with status (QUEUED, SENT, FAILED), attempts, nextAttemptAt.

Plan — FREE (5 members, 3 programs), PRO (50 members, 20 programs), ENTERPRISE (unlimited).

## Functional Requirements (summary + details)
### 1) Authentication (Signup → Verify → Login/Logout → Refresh token rotation)
#### Description
* Signup: create user; if organization name provided -> create organization and the user becomes Admin. Return verification token in response
* Verify email: client calls verify endpoint with token + email. That call creates an OutboxEmail entry and will return successful | queued depending on simulate mode and worker attempts. When OutboxWorker marks SENT, the token is marked used and user becomes VERIFIED.
* Login: returns sessionId (Bearer token) + refresh token; sessions saved in auth_sessions, refresh tokens saved separately.
* Refresh: rotate refresh token (invalidate old, create new sessionId and new refresh token).
* Logout: revoke session and associated refresh tokens. Always return 204 No Content.

#### User stories
* As a new user I want to register and verify my email so I can log in.
* As a user I want login to create a session and refresh token so I can keep working.

#### Acceptance Criteria
* If sign-up email already exists and is VERIFIED -> 409 conflict.
* Verify endpoint returns: 200 {message:"successful"} if outbox attempt succeeded; 200 {message:"queued"} if queued for retries; 200 {message:"failed"} if all attempts failed.
* Refresh token rotation: using refresh token must revoke old one and create a new one; old token cannot be reused.

#### Edge cases
* Token expired -> 400/422 TokenExpiredException.
* Token already used -> 400/422 TokenUsedException.
* Verify called multiple times -> idempotent response (email already verified => "email already verified").

### 2) Organization Profile
Fields to upsert: logoUrl, website, industry, description
Completeness: percentage of four fields filled (logoUrl, website, industry, description). Return missing fields list.

#### User stories
* As an Org Admin I want to set my org profile so people can identify my org.
* As an Admin I want to see completeness % so I know what's missing.

#### Acceptance Criteria
* GET /org/me returns completeness percent + missing fields; 0% if no fields.
* PATCH /org/update-org-profile upserts fields (no duplicate records).
* Invalid website (bad URL) -> 422 VALIDATION_ERROR.
* Invalid logo file type/size -> 422 

#### Edge cases
* Logo validation network errors -> 422 (explain host resolution error).
* Repeated updates overwrite previous data (last write wins).

### 3) RBAC & Team (Admin, Manager, Member; invite/accept flow)Model
* user.role = {ADMIN, MANAGER, MEMBER}
* user.organization is required for registered users.

#### Invite flow (Admin only)
* Admin calls POST /v1/admin/invite-member with {email, role}. System creates Invite (token) and saves OutboxEmail. Returns token in response (since no real email).
* Invited user calls POST /v1/admin/accept-invite with token (must be logged in and email match). Accept sets user.organization, role, increments org.membersCount.

#### Acceptance Criteria
* Only Admin can create invites -> 403 if not.
* Invites expire after configured days (default 7) -> expired invites set EXPIRED and return 404/TOKEN_EXPIRED.
* If invite email already belongs to an org -> 409 conflict.

#### Edge cases
* Duplicate active invite -> 409.
* Invite accepted when org is at members limit -> 422/LimitExceeded (reject).
* Invite acceptance must be transactional: check limits atomically (lock org row).

### 4) Programs (CRUD; filtering)
#### Model 
* Program { id, organization, name, description, status (ACTIVE/ARCHIVED), startAt?, endAt?, createdAt, updatedAt }

#### User stories
* As Admin/Manager I want to create a program so my org can use it.
* As Admin/Manager I want to archive a program so it is not active.

#### Acceptance Criteria
* Create: name required, enforce program name uniqueness per org.
* Update: only Admin/Manager can update; renaming to an existing name -> 409.
* List: GET /v1/programs returns active programs for org.
* Archive: idempotent; if archived already returns current program details.

#### Edge cases
* startAt/endAt validation (end must be after start) -> 422 DateValidationException.
* Race on create name uniqueness: handle DB constraint (catch DataIntegrityViolationException) and return 409.

### 5) Plans & Limits
#### Plans
* FREE: members 5, programs 3
* PRO: members 50, programs 20
* ENTERPRISE: unlimited

#### Behavior
* Creation of members/programs/enabling invites must check org current count against plan limit.
* Enforce plan limits atomically in service (e.g., select org FOR UPDATE or a DB counter + transaction) to avoid races.

#### Acceptance Criteria
* If org at or over limit -> attempts to create invite/program return 422/LimitExceededException.
* Admin can change plan (PATCH /v1/org/{orgId}/plan). Downgrade is blocked if current usage exceeds the target limits -> return meaningful error with current vs allowed numbers.

### 6) Email Outbox (mock)
#### Model
* OutboxEmail { id, toEmail, subject, body, attempts, maxAttempts, status (QUEUED,SENT,FAILED), nextAttemptAt, forceFail, verificationToken, createdAt }

#### Worker
* OutboxWorker.processOutboxAttempt(UUID outboxId) attempts send (simulated), increments attempts, sets nextAttemptAt or marks SENT/FAILED.
* triggerRetriesForToken(String token) loops (async) and retries when nextAttemptAt reached, but do this in background so verify endpoint returns quickly.

#### Simulate modes
* SIMULATE_EMAIL_BEHAVIOUR env var: ALWAYS_SUCCESS, ALWAYS_FAIL, RANDOM.
* Retry policy: default 2 retries after initial attempt (total attempts 3) with 2s delay

#### Acceptance Criteria
* Every queued email eventually becomes SENT or FAILED.
* Worker must handle retries gracefully and not block the request thread.
* Verify endpoint: if outbox attempt returns QUEUED -> return "queued" and start worker retries in background.

#### Edge cases
* ForceFail flag -> ensure deterministic failure for tests.
* Worker should re-check DB for fresh state to avoid LazyInitialization / session problems; use new transaction per attempt.

### 7) Health & Metrics
* GET /.well-known/health must return { status, checks: { db: "ok"|"not_ok" }, details } and 200 when OK; 503 when DB down.
* GET /metrics returns small Prometheus-style metrics (uptime, memory, db_up).
* Unauthenticated: these endpoints should be public (permitAll / web.ignoring in security config).

### 8) Error Envelope (standard)
#### Return all errors wrapped in:
``` bash
{ "error": { "code": "SOME_CODE", "message": "...", "details": [ { "field":"email" , "rule":"format" } ], "trace_id": "req_123"
```
* Map validation exceptions to INVALID_REQUEST with 422 or 400 as you prefer.
* Map API exceptions (TokenExpired, LimitExceeded, etc.) to appropriate HTTP statuses per exception class.