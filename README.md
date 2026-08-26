# Synapse Backend 🧠

Synapse is a Spring Boot backend for turning uploaded study notes into structured learning resources. Users can register, upload PDF, Word (`.docx`), plain text, or Markdown notes, generate AI-powered summaries, create flashcard decks, and generate quizzes from saved notes.

The API is secured with short-lived JWT bearer access tokens and rotating refresh token cookies, persists data in PostgreSQL, manages schema changes with Flyway, and exposes interactive OpenAPI documentation through Swagger UI.

## Features ✨

- JWT-based registration and login
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
- Swagger/OpenAPI documentation
- Integration tests using Testcontainers
- In-memory per-user and per-IP rate limiting
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
```

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

Local development configuration lives in:

```text
src/main/resources/application-dev.yml
```

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

Groq is the primary LLM client used by generation flows. Gemini client configuration is present as an alternate implementation, but Groq is currently selected by Spring.

For production deployments, prefer setting secrets and profile values through environment variables rather than committing profile-specific configuration.

## API Overview 📚

Most endpoints require a JWT access token:

```http
Authorization: Bearer <accessToken>
```

### Authentication 🔐

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Register a new user and receive an access token | No |
| `POST` | `/api/auth/login` | Log in and receive an access token | No |
| `POST` | `/api/auth/refresh` | Exchange the refresh token cookie for a new access token | No |
| `POST` | `/api/auth/logout` | Revoke the current refresh token and clear the cookie | No |

Register and login also set a `refreshToken` cookie. The cookie is `HttpOnly`, `Secure`, `SameSite`-restricted, scoped
to `/api/auth`, and valid for 30 days. Only a SHA-256 hash of each refresh token is stored server-side, alongside its
user, expiry, and revocation state.

`POST /api/auth/refresh` rotates the token it is given: the presented refresh token is revoked and a replacement cookie
is issued, so each refresh token can be used only once. A missing, expired, revoked, or already used token returns
`401`. Revocation is a single conditional update, so if two requests race with the same refresh token, exactly one
rotates it and the other receives `401`. Rotation is transactional, so a failure partway through leaves the
presented token usable instead of ending the session. Browser clients must send the request with credentials included so the cookie
is attached.

### User 👤

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| `GET` | `/api/user/details` | Get the authenticated user's details | Yes |

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

## Example Flow 🔄

1. Register or log in.
2. Copy the returned `accessToken` and keep the `refreshToken` cookie.
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

- Authentication
- Refresh token issuing, rotation, expiry, revocation, concurrent rotation, rotation rollback, and logout
- User details
- Note summary/list/get/delete flows
- Flashcard generate/list/get/delete flows
- Quiz generate/list/get/delete flows
- Quiz question creation/deletion and difficulty updates
- Quiz score creation, validation, ownership, ordering, and history retrieval
- Rate limiting of AI, authenticated, login, and registration requests
- PDF, DOCX, plain text, and Markdown extraction

LLM-backed endpoint tests mock the `LLMClient`, so tests do not require real LLM API calls or tokens.

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
- Expired and revoked refresh tokens are kept in the database. There is no scheduled cleanup job yet.
- Refresh token reuse is rejected but does not revoke the rest of that user's active refresh tokens.
- The local application profile is development-oriented and should be adjusted for production deployment.
