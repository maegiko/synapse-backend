# Synapse Backend 🧠

Synapse is a Spring Boot backend for turning uploaded study notes into structured learning resources. Users can register, upload PDF, Word (`.docx`), plain text, or Markdown notes, generate AI-powered summaries, create flashcard decks, and generate quizzes from saved notes.

The API is secured with short-lived JWT bearer access tokens and rotating refresh token cookies, persists data in PostgreSQL, manages schema changes with Flyway, and exposes interactive OpenAPI documentation through Swagger UI during local development.

## Features ✨

- JWT-based registration and login, with mandatory email verification before an account can log in
- Emailed single-use verification links for registration and confirmed email changes, sent through Resend
- Emailed single-use password reset links, which end every session of the account they reset
- Short-lived access tokens with rotating refresh tokens in `HttpOnly` cookies, plus refresh and logout endpoints
- Authenticated user profile endpoint
- PDF, Word (`.docx`), plain text (`.txt`), and Markdown (`.md`) note upload and AI-generated note summaries
- Saved note listing, retrieval, and deletion
- AI-generated flashcard decks from saved notes
- Flashcard deck listing, retrieval, deletion, and manual card management
- AI-generated quizzes from saved notes
- Quiz listing, retrieval, deletion, difficulty settings, and manual question management
- Personal-practice score saving and newest-first score history
- PostgreSQL persistence with Flyway migrations
- Swagger/OpenAPI documentation in development, disabled in production
- Integration tests using Testcontainers
- In-memory per-user and per-IP rate limiting
- Scheduled cleanup of never-verified accounts and expired verification tokens
- Checkstyle linting

## Tech Stack 🛠️

- Java 25
- Spring Boot 4
- Spring MVC
- Spring Security
- Spring OAuth2 Resource Server
- Spring Data JPA
- PostgreSQL
- Flyway
- Gradle
- Testcontainers
- PDFBox
- Apache POI
- Caffeine
- springdoc-openapi
- Groq/Gemini-ready LLM client configuration

## Requirements 📋

- Java 25
- Docker
- Docker Compose
- A Groq API key for the currently configured generation client
- A Resend API key for verification email delivery
- A JWT secret with at least 32 bytes

## Getting Started 🚀

Clone the repository:

```bash
git clone <repository-url>
cd synapse-backend
```

Create an environment file:

```bash
cp .env.example .env
```

Fill in the required values:

```properties
JWT_SECRET=replace-with-at-least-32-bytes
GEMINI_API_KEY=optional-if-not-used
GROQ_API_KEY=replace-with-your-groq-api-key
RESEND_API_KEY=replace-with-your-resend-api-key
```

`RESEND_API_KEY` is created in the Resend dashboard under **API Keys** and must have sending permission for the
verified sending domain. Never commit it; `.env` is git-ignored.

Start PostgreSQL:

```bash
docker compose up -d
```

Run the application:

```bash
./gradlew bootRun
```

The API will be available at:

```text
http://localhost:8080
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

The root route also redirects to Swagger UI:

```text
http://localhost:8080/
```

## Configuration ⚙️

Shared configuration lives in `src/main/resources/application.yml`. Environment-specific overrides live in:

```text
src/main/resources/application-dev.yml
src/main/resources/application-prod.yml
```

The `dev` profile is the default when no profile is selected. A deployment must explicitly set
`SPRING_PROFILES_ACTIVE=prod`.

The default local database is provided by `docker-compose.yml`:

```text
Database: synapse_dev
Username: dev_user
Password: dev_password
Port:     5432
```

The application imports `.env` using:

```properties
spring.config.import=optional:file:.env[.properties]
```

The development profile sets JWT access tokens to expire after `15m`, refresh tokens to expire after `30d`, and limits
multipart uploads to `10MB`:

```yaml
jwt:
  issuer: synapse
  access-token-ttl: 15m
  refresh-token-ttl: 30d
  secret: ${JWT_SECRET}

auth:
  refresh-cookie:
    secure: true
    same-site: None
```

`auth.refresh-cookie` controls the `Secure` and `SameSite` attributes of the refresh token cookie. The defaults suit a
browser frontend served from a different origin over HTTPS. Set `same-site` to `Lax` when the frontend and API are
served from the same site.

Email verification and delivery are configured with:

```yaml
auth:
  email-verification:
    frontend-url: ${EMAIL_VERIFICATION_URL:http://localhost:5173/verify-email}
    registration-token-ttl: 1h
    email-change-token-ttl: 24h
    unverified-retention: 7d
    cleanup-interval: 1h

  password-reset:
    frontend-url: ${PASSWORD_RESET_URL:http://localhost:5173/reset-password}
    token-ttl: 30m

email:
  resend:
    api-key: ${RESEND_API_KEY}
    from: ${RESEND_FROM:Synapse <no-reply@studysynapse.app>}
    api-url: ${RESEND_API_URL:https://api.resend.com/emails}
    connect-timeout: 5s
    read-timeout: 10s
```

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `RESEND_API_KEY` | Yes | none | Resend API key used as `Authorization: Bearer <key>` |
| `EMAIL_VERIFICATION_URL` | No | `http://localhost:5173/verify-email` | Frontend page verification links point at; set it to `https://studysynapse.app/verify-email` in production |
| `PASSWORD_RESET_URL` | No | `http://localhost:5173/reset-password` | Frontend page password reset links point at; set it to `https://studysynapse.app/reset-password` in production |
| `RESEND_FROM` | No | `Synapse <no-reply@studysynapse.app>` | Sender address, which must belong to a domain verified in Resend |
| `RESEND_API_URL` | No | `https://api.resend.com/emails` | Resend send-email endpoint |

Verification and password reset links are `{frontend-url}?token={urlEncodedToken}`. The link opens the frontend, which
posts the token to `POST /api/auth/email/verify` or `POST /api/auth/password/reset`; no state-changing backend route is
ever opened directly from an email, because mail clients and scanners follow links on their own.

The two token lifetimes differ on purpose. Confirming a registration link signs the account in, which makes that link a
credential and not merely a proof of address, so it lives for an hour and a lapsed one is recovered with the resend
endpoint. An email-change link issues no session, so it keeps the full day.

Groq is the primary LLM client used by generation flows. Gemini client configuration is present as an alternate implementation, but Groq is currently selected by Spring.

For production deployments, prefer setting secrets and profile values through environment variables rather than committing profile-specific configuration.

## Production Deployment 🚢

The repository includes a multi-stage `Dockerfile` that builds the Spring Boot jar with Java 25 and runs it as a
non-root user on a Java 25 JRE. Build it with:

```bash
docker build -t synapse-backend .
```

The production profile expects a PostgreSQL database and the following environment variables:

| Variable | Required | Production default | Meaning |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | none | Must be `prod` for a deployment |
| `DB_HOST` | Yes | none | PostgreSQL hostname reachable from the backend |
| `DB_PORT` | Yes | none | PostgreSQL port, normally `5432` |
| `DB_NAME` | Yes | none | PostgreSQL database name |
| `DB_USERNAME` | Yes | none | PostgreSQL username |
| `DB_PASSWORD` | Yes | none | PostgreSQL password |
| `JWT_SECRET` | Yes | none | Random secret of at least 32 bytes; changing it invalidates existing access tokens |
| `GROQ_API_KEY` | Yes for AI generation | empty | Groq API key used by the primary LLM client |
| `RESEND_API_KEY` | Yes for email | none | Resend API key for verification and password-reset messages |
| `FRONTEND_ORIGIN` | No | `https://studysynapse.app` | Exact browser origin allowed by CORS, without a trailing slash |
| `EMAIL_VERIFICATION_URL` | No | `https://studysynapse.app/verify-email` | Frontend verification page used in email links |
| `PASSWORD_RESET_URL` | No | `https://studysynapse.app/reset-password` | Frontend reset page used in email links |
| `PORT` | No | `8080` | HTTP port exposed by the platform |
| `RESEND_FROM` | No | `Synapse <no-reply@studysynapse.app>` | Verified sender used for transactional email |

For a small container, this is a reasonable starting point for `JAVA_TOOL_OPTIONS`:

```text
-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=20 -XX:+UseSerialGC
```

Flyway runs automatically at startup, so deploy the backend only after the database exists and keep the database
persistent across backend redeployments. The production connection pool is capped at five connections to suit a
small hosted PostgreSQL instance.

Use `GET /actuator/health` for the platform health check. It is public and returns only overall health; no other
Actuator endpoint is exposed. The production profile also honours reverse-proxy forwarding headers so client-address
rate limits use the original client address supplied by the hosting platform.

Production serves neither Swagger UI nor the OpenAPI JSON. `/`, `/swagger-ui.html`, and `/v3/api-docs` return `404`
under the `prod` profile. They remain available locally under the default `dev` profile.

## API Overview 📚

Most endpoints require a JWT access token:

```http
Authorization: Bearer <accessToken>
```

### Authentication 🔐

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Register an unverified user and email them a verification link | No |
| `POST` | `/api/auth/login` | Log in and receive an access token | No |
| `POST` | `/api/auth/email/verify` | Confirm an emailed verification link, signing a confirmed registration in | No |
| `POST` | `/api/auth/email/resend` | Resend a registration verification link | No |
| `POST` | `/api/auth/password/forgot` | Email a password reset link | No |
| `POST` | `/api/auth/password/reset` | Set a new password from a reset link | No |
| `POST` | `/api/auth/refresh` | Exchange the refresh token cookie for a new access token | No |
| `POST` | `/api/auth/logout` | Revoke the current refresh token and clear the cookie | No |
| `PUT` | `/api/auth/password` | Change the authenticated user's password | Yes |

`POST /api/auth/register` requires a 2–100 character full name containing no numbers. It stores the full name with each
word capitalised — `ada lovelace` and `ADA LOVELACE` are both saved as `Ada Lovelace`, while a word typed in a mixture
of cases, such as `McDonald`, is kept as it was written. It
returns `202` with the address the link was sent to and a message telling the client to check its email. It issues no access token and sets no refresh cookie: the account is created with `email_verified_at` null
and cannot log in until the link is confirmed. Logging in with the right password on an unverified account returns
`401` with a message naming verification, while a wrong password on the same account still returns the generic
`Invalid email or password.` An address that already belongs to a **verified** account returns `409` as before. An
address that belongs to an **unverified** account gets the same `202` and a replacement link, and its stored password,
name, and time zone are never overwritten.

`POST /api/auth/email/verify` takes `{ "token": "..." }`, consumes the single-use token, and returns `200` with a
`kind` property naming the kind of link it was. A `REGISTRATION` token marks the account verified and signs it in,
answering with the account's name, address, and an access token, and setting the same refresh cookie login sets. An
`EMAIL_CHANGE` token moves the account to its new address after re-checking that nobody else has claimed it, returning
`409` if they have; it answers with only `kind` and the new address, and issues no token and no cookie, because whoever
confirms it normally already has a session that a new refresh cookie would needlessly replace. Clients must branch on
`kind` rather than on whether the visitor happens to be signed in already: somebody signed into one account can open a
registration link for another. Missing, unknown, expired, replaced, and already used tokens all return the same
generic `400`, so a link opened twice succeeds once and issues exactly one session.

`POST /api/auth/email/resend` takes `{ "email": "..." }` and always returns `204`, whether the address is unknown,
already verified, or still pending, so it cannot be used to discover who has an account. Only an unverified account
causes an email to be sent, and the replacement link invalidates the previous one.

`POST /api/auth/password/forgot` takes `{ "email": "..." }`, normalises it exactly like registration, and **always**
returns `204`. Unknown addresses, accounts that have never been verified, live accounts, and a failed email provider
are answered identically, so the endpoint cannot be used to discover who has an account; a provider failure is logged
and never reaches the caller. Only a verified account is actually sent a link, and a new link invalidates that user's
previous one. The request is limited to 3 per hour per normalised address and, separately, per client address, and the
limits are checked before the account is looked up so an unknown address costs the same as a real one.

`POST /api/auth/password/reset` takes `{ "token": "...", "newPassword": "..." }`, consumes the single-use token,
BCrypt-hashes the new password, and returns `204`. The new password must satisfy the same 8-64 character rule as
registration. Missing, unknown, expired, replaced, and already used tokens all return the same generic `400`, and a
token from a verification email is never accepted here: reset tokens live in their own table and are consumed by their
own endpoint, so neither kind of link can do the other's job.

A successful reset revokes **every** refresh token of that user and clears the caller's refresh cookie, so all of their
sessions have to sign in again. It does not sign the caller in: no access token is returned, and the client should
route to login. Access tokens are not blacklisted, so one issued before the reset stays usable for the rest of its
15-minute lifetime.

Reset tokens are 32 cryptographically random bytes, URL-safe Base64 encoded without padding, stored only as a SHA-256
hash, and valid for 30 minutes. Consumption is a single conditional update, so two concurrent resets with the same link
race for one row and only the first succeeds. Consumption, the password write, and the session revocation are one
transaction: if either write fails, the link stays usable.

Verification tokens are 32 cryptographically random bytes, URL-safe Base64 encoded without padding. Only their SHA-256
hash is stored, alongside the user, target address, purpose, expiry, and consumption/invalidation state. Consumption is
a single conditional update, so two concurrent confirmations of the same link race for one row and only the first
succeeds. A registration token is valid for one hour and an email-change token for 24 hours by default.

Login also sets a `refreshToken` cookie. The cookie is `HttpOnly`, `Secure`, `SameSite`-restricted, scoped
to `/api/auth`, and valid for 30 days. Only a SHA-256 hash of each refresh token is stored server-side, alongside its
user, expiry, and revocation state.

`POST /api/auth/refresh` rotates the token it is given: the presented refresh token is revoked and a replacement cookie
is issued, so each refresh token can be used only once. A missing, expired, revoked, or already used token returns
`401`. Revocation is a single conditional update, so if two requests race with the same refresh token, exactly one
rotates it and the other receives `401`. Rotation is transactional, so a failure partway through leaves the
presented token usable instead of ending the session. Browser clients must send the request with credentials included so the cookie
is attached.

`PUT /api/auth/password` takes `currentPassword` and `newPassword`, verifies the current password, and returns `401` if
it is wrong. On success it returns `204`, revokes every refresh token belonging to the user, and clears the refresh
cookie, so a password change signs every session out. The client must discard its access token as well, because
existing access tokens stay valid until they expire.

### User 👤

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| `GET` | `/api/user/details` | Get the authenticated user's details | Yes |
| `PATCH` | `/api/user/details` | Update the authenticated user's full name and/or time zone | Yes |
| `POST` | `/api/user/email-change` | Request a confirmed change of email address | Yes |
| `GET` | `/api/user/streak` | Get the authenticated user's study streak | Yes |

`PATCH /api/user/details` accepts an optional `fullName` and an optional `timeZone`, and requires at least one of them.
Only the supplied fields are changed, and both are trimmed before their length limits are applied. A supplied full
name is capitalised the same way registration capitalises it. The email address
can no longer be changed here; an `email` property in the body is ignored. The response is the updated details; no new
access token is issued, so `GET /api/user/details` stays the source of current profile data.

`POST /api/user/email-change` takes `{ "email": "..." }`, normalises it exactly like registration, and emails a
single-use confirmation link to the proposed address. It returns `202` with the pending address and its expiry, `204`
when the proposed address is the one the user already has, and `409` when the address already belongs to another
account. Nothing about the account changes until the link is confirmed: the current address keeps working, including
for login. A newer request invalidates the pending one, and an abandoned request simply expires without reserving the
address.

### Notes 📝

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/api/notes/summarise` | Upload a PDF, `.docx`, `.txt`, or `.md` note, generate a summary, and save it | Yes |
| `GET` | `/api/notes/list` | List saved note summaries | Yes |
| `GET` | `/api/notes/{id}` | Get one saved note summary | Yes |
| `DELETE` | `/api/notes/{id}` | Delete a saved note | Yes |

### Flashcards 🃏

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/api/flashcards/generate` | Generate and save flashcards from a saved note | Yes |
| `GET` | `/api/flashcards/list` | List saved flashcard decks | Yes |
| `GET` | `/api/flashcards/{deckId}` | Get one flashcard deck | Yes |
| `POST` | `/api/flashcards/{deckId}` | Add a flashcard to a deck | Yes |
| `DELETE` | `/api/flashcards/{deckId}` | Delete a flashcard deck | Yes |
| `DELETE` | `/api/flashcards/{deckId}/cards/{cardId}` | Delete a flashcard from a deck | Yes |
| `POST` | `/api/flashcards/{deckId}/complete` | Mark a deck as completed and record study activity | Yes |

### Quizzes ❓

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/api/quiz/generate` | Generate and save a quiz from a saved note | Yes |
| `GET` | `/api/quiz/list` | List saved quizzes with question previews | Yes |
| `GET` | `/api/quiz/{quizId}` | Get one quiz with questions and answers | Yes |
| `POST` | `/api/quiz/{quizId}/questions` | Add a question and answers to a quiz | Yes |
| `DELETE` | `/api/quiz/{quizId}` | Delete a quiz | Yes |
| `DELETE` | `/api/quiz/{quizId}/questions/{questionId}` | Delete a question from a quiz | Yes |
| `PUT` | `/api/quiz/{quizId}/difficulty` | Set quiz difficulty from 1 to 5 | Yes |
| `POST` | `/api/quiz/{quizId}/score` | Save a personal-practice score | Yes |
| `GET` | `/api/quiz/{quizId}/score/list` | List saved scores from newest to oldest | Yes |

### Study Streaks 🔥

`GET /api/user/streak` returns the authenticated user's study streak:

```json
{
  "currentStreak": 6,
  "longestStreak": 14,
  "activeToday": true,
  "lastActiveDate": "2026-08-28"
}
```

A day counts once a qualifying study action succeeds and its data is saved:

- `POST /api/notes/summarise`
- `POST /api/flashcards/generate`
- `POST /api/quiz/generate`
- `POST /api/quiz/{quizId}/score`
- `POST /api/flashcards/{deckId}/complete`

Streak days are UTC calendar days decided by the server; the client never sends a date. Several qualifying actions on
one day still count as one day, and failed requests award nothing. `currentStreak` counts back from the most recent
active day and only while that day is today or yesterday, so a missed day resets it to `0`. `longestStreak` is the
longest run in the user's history. A user with no activity gets zeros and a null `lastActiveDate`.

## Example Flow 🔄

1. Register, then confirm the emailed verification link, which signs the new account in. Existing users just log in.
2. Copy the `accessToken` returned by that confirmation or by login, and keep the `refreshToken` cookie.
3. Upload a PDF, `.docx`, `.txt`, or `.md` file to `/api/notes/summarise`.
4. Use the saved note id to generate flashcards or a quiz.
5. Add or delete individual flashcards/questions as needed.
6. Set the quiz difficulty and complete the quiz in the client.
7. Save the practice score and retrieve previous attempts.
8. Retrieve saved learning resources from the list/get endpoints.
9. Call `/api/auth/refresh` when the access token expires, and `/api/auth/logout` to end the session.

Example registration request:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Ada Lovelace",
    "email": "ada@example.com",
    "password": "password123"
  }'
```

It answers `202` and sends the verification email:

```json
{
  "email": "ada@example.com",
  "message": "Check your email for a verification link to finish creating your account."
}
```

Example verification request, with the token the frontend page read from the link:

```bash
curl -X POST http://localhost:8080/api/auth/email/verify \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<rawTokenFromTheLink>"
  }'
```

A registration link answers `200`, sets the refresh cookie, and signs the new account in:

```json
{
  "kind": "REGISTRATION",
  "fullName": "Ada Lovelace",
  "email": "ada@example.com",
  "accessToken": "<accessToken>"
}
```

An email-change link answers `200` with the address the account now uses, and nothing else:

```json
{
  "kind": "EMAIL_CHANGE",
  "email": "ada.lovelace@example.com"
}
```

Example email-change request:

```bash
curl -X POST http://localhost:8080/api/user/email-change \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "ada.lovelace@example.com"
  }'
```

Example forgotten-password request, which answers `204` whatever the address turns out to be:

```bash
curl -X POST http://localhost:8080/api/auth/password/forgot \
  -H "Content-Type: application/json" \
  -d '{
    "email": "ada@example.com"
  }'
```

Example password reset, with the token the frontend reset page read from the link:

```bash
curl -X POST http://localhost:8080/api/auth/password/reset \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<rawTokenFromTheLink>",
    "newPassword": "a-new-password"
  }'
```

It answers `204`, clears the refresh cookie, and signs nobody in: log in with the new password afterwards.

Example refresh request, reusing the cookie saved at login:

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -b cookies.txt \
  -c cookies.txt
```

Example note upload:

```bash
curl -X POST http://localhost:8080/api/notes/summarise \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@/path/to/notes.pdf"
```

Example quiz generation:

```bash
curl -X POST http://localhost:8080/api/quiz/generate \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "noteId": "<noteId>"
}'
```

Example manual quiz question creation:

```bash
curl -X POST http://localhost:8080/api/quiz/<quizId>/questions \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What does a reinforcing loop do?",
    "questionType": "MULTIPLE_CHOICE",
    "answers": [
      { "answer": "Amplifies change", "isCorrect": true },
      { "answer": "Always reduces change", "isCorrect": false },
      { "answer": "Stores uploaded PDFs", "isCorrect": false },
      { "answer": "Expires JWT tokens", "isCorrect": false }
    ]
  }'
```

Example difficulty update:

```bash
curl -X PUT http://localhost:8080/api/quiz/<quizId>/difficulty \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "difficulty": 3
  }'
```

Example score creation:

```bash
curl -X POST http://localhost:8080/api/quiz/<quizId>/score \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "score": 8
  }'
```

Scores are calculated by the client for personal practice. The backend verifies that a score is non-negative and does not exceed the quiz question count. Each attempt stores the question count at completion time so historical results remain meaningful if the quiz changes later.

Example score history retrieval:

```bash
curl http://localhost:8080/api/quiz/<quizId>/score/list \
  -H "Authorization: Bearer <accessToken>"
```

## Database Migrations 🗄️

Flyway migrations are stored in:

```text
src/main/resources/db/migration
```

Current migration coverage includes:

- Users
- Notes
- Flashcards
- Quizzes
- Quiz difficulty and score history
- Refresh tokens
- Study streak activity days
- Email verification state and verification tokens

The development profile uses:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

This means Hibernate validates the schema and Flyway owns schema creation/evolution.

## Testing ✅

Run the full test suite:

```bash
./gradlew test
```

Run linting:

```bash
./gradlew lint
```

Run both:

```bash
./gradlew test lint
```

Integration tests use Testcontainers with PostgreSQL, so Docker must be running before executing tests.

The test suite covers:

- Authentication, including that an unverified account cannot log in
- Email verification: registration state, email contents and link and expiry, confirmation, the session a confirmed
  registration issues, provider failure recovery, and the migration backfill
- Verification tokens: unknown, expired, invalidated, consumed, reused, and concurrently consumed links
- Verification resending and its deliberately identical responses
- Confirmed email changes, including uniqueness re-checks, races, replacement requests, and abandonment
- Password resets: the non-enumerating forgot response for unknown, unverified, verified, and provider-failure cases,
  both rate limits, token storage and expiry, replacement, reuse, concurrent consumption, purpose isolation from
  verification tokens, session revocation, cookie clearing, and transaction rollback
- Cleanup of never-verified accounts and expired verification tokens
- Refresh token issuing, rotation, expiry, revocation, concurrent rotation, rotation rollback, and logout
- User details
- Note summary/list/get/delete flows
- Flashcard generate/list/get/delete flows
- Quiz generate/list/get/delete flows
- Quiz question creation/deletion and difficulty updates
- Quiz score creation, validation, ownership, ordering, and history retrieval
- Streak activity awarding, streak calculation, UTC day boundaries, and ownership isolation
- Flashcard deck completion, ownership, and same-day idempotency
- Rate limiting of AI, authenticated, login, registration, and verification-resend requests
- PDF, DOCX, plain text, and Markdown extraction

LLM-backed endpoint tests mock the `LLMClient`, so tests do not require real LLM API calls or tokens. The shared
integration-test base class replaces the `EmailClient` with a mock for every test and points the Resend client at an
unreachable URL with a dummy key, so no test can send email or reach the provider.

## Project Structure 🧱

```text
src/main/java/com/synapse/backend
├── ai             # LLM client abstractions, provider clients, prompts, and AI exceptions
├── auth           # Registration, login, refresh tokens, logout, auth DTOs, and auth exceptions
├── config         # Shared application configuration
├── docs           # Swagger/OpenAPI configuration and docs redirect
├── flashcards     # Flashcard controllers, services, DTOs, entities, and repositories
├── notes          # Note controllers, services, DTOs, entities, and repositories
├── quiz           # Quiz controllers, services, DTOs, entities, enums, and repositories
├── security       # JWT and Spring Security configuration
├── shared         # Shared errors, exceptions, and file parsing utilities
├── streak         # Study streak controller, services, DTO, entity, and repository
└── user           # User entity, repository, service, controller, and DTOs
```

## Error Handling 🚦

The API returns structured error responses through a global exception handler:

```json
{
  "message": "Error message"
}
```

Common response codes:

- `400` for validation, bad input, unsupported files, and invalid requests
- `401` for unauthenticated or invalid-auth requests
- `404` for resources that do not exist or do not belong to the user
- `409` for duplicate registration conflicts
- `502` for LLM provider failures or invalid LLM responses

## API Documentation 📖

OpenAPI documentation is generated automatically from controller annotations.

When the app is running, visit:

```text
http://localhost:8080/swagger-ui.html
```

The OpenAPI JSON is available at:

```text
http://localhost:8080/v3/api-docs
```

## Development Notes 🧭

- Keep generated resources user-scoped. All saved notes, flashcards, and quizzes should be accessed only by the authenticated owner.
- Do not call real LLM APIs from integration tests. Mock `LLMClient` instead.
- Add Flyway migrations for schema changes instead of relying on Hibernate schema generation.
- Keep controllers thin and place business orchestration in services.
- Keep persistence mapping/query logic in persistence services.
- Run `./gradlew test lint` before opening a pull request.

## Rate Limiting 🚦

Requests are counted in a bounded in-memory Caffeine cache per application instance, in fixed windows. Exceeding a limit
returns HTTP 429 with the standard error body and a `Retry-After` header holding the seconds until the window resets,
rounded up.

| Scope | Limit | Counted per |
| --- | --- | --- |
| `POST /api/notes/summarise`, `POST /api/flashcards/generate`, `POST /api/quiz/generate` | 3 per minute and 50 per day | JWT user id |
| Other authenticated `/api/**` requests | 120 per minute | JWT user id |
| `POST /api/auth/login` | 10 per 15 minutes | Normalized email, and separately client address |
| `POST /api/auth/register` | 3 per hour | Client address |
| `POST /api/auth/email/resend` | 3 per hour | Normalized email, and separately client address |
| `POST /api/auth/password/forgot` | 3 per hour | Normalized email, and separately client address |
| `POST /api/user/email-change` | 3 per hour | JWT user id |

CORS preflight requests are not rate limited.

Limits are configured in `src/main/resources/application-dev.yml`:

```yaml
ratelimit:
  enabled: true
  ai:
    limit: 3
    window: 1m
  ai-daily:
    limit: 50
    window: 1d
  login:
    limit: 10
    window: 15m
  register:
    limit: 3
    window: 1h
  verification-resend:
    limit: 3
    window: 1h
  email-change:
    limit: 3
    window: 1h
  password-reset:
    limit: 3
    window: 1h
  api:
    limit: 120
    window: 1m
```

Setting `ratelimit.enabled` to `false` turns limiting off.

## Current Limitations ⚠️

- Only PDF, DOCX, plain text (`text/plain`), and Markdown (`text/markdown`) uploads are currently supported for note summarisation.
- Legacy `.doc` Word files are not supported, only `.docx`.
- LLM generation quality depends on the configured provider and prompt behavior.
- Rate limiting is in-memory and single-instance. Counters are not shared between instances and are lost on restart.
- Rate limiting uses the direct client address, so a reverse proxy must be accounted for before deploying behind one.
- Expired and revoked refresh tokens are kept in the database. There is no scheduled cleanup job for them.
- Verification emails are sent after the account and token are committed, in a separate step. A provider failure
  returns `502` and leaves an unverified account that the resend endpoint can recover; the two are deliberately not one
  transaction.
- Password reset emails are sent the same way, but a provider failure is swallowed and logged rather than returned,
  because changing the response would tell the caller that the address has an account.
- A password reset cannot revoke access tokens that have already been issued, so a stolen access token survives the
  reset for the rest of its 15-minute lifetime. Only refresh tokens are revoked.
- Never-verified accounts are deleted seven days after they are created, and expired verification and password reset
  tokens are swept, by scheduled sweeps that run in every instance.
- Refresh token reuse is rejected but does not revoke the rest of that user's active refresh tokens.
- The local application profile is development-oriented and should be adjusted for production deployment.
