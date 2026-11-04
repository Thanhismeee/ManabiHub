# MHB-32 Student AI Chat

## Scope

MHB-32 lets an authenticated Student ask an AI assistant about one lesson block. The assistant is learning support only and is not an official assessment or Teacher response.

## API

- `GET /api/v1/student/courses/{courseId}/lesson-blocks/{lessonBlockId}/ai-chat/eligibility`
- `POST /api/v1/student/courses/{courseId}/lesson-blocks/{lessonBlockId}/ai-chat/messages`

Both endpoints require `ROLE_STUDENT`. The backend resolves the Student from the JWT and never accepts a user ID from the client.

## Eligibility

All checks are enforced server-side:

1. The lesson block belongs to the requested course.
2. The authenticated Student has an `ACTIVE` enrollment in the course.
3. `AI_ENABLED` and `AI_CHATBOT_ENABLED` are enabled in `system_settings`.
4. The course has `ai_supported = true`.
5. The course price is at least `AI_SUPPORT_PRICE_FLOOR`.
6. Per-minute and daily usage limits have not been reached.

The eligibility endpoint returns an intentional unavailable state for business restrictions. Sending a message while ineligible returns HTTP 403.

## Context Boundary

The provider receives only allowed course metadata and the exact requested lesson block. Text, quiz question/options, flashcards, writing prompt, and rubric can be included. Quiz answers and other lesson blocks are excluded.

The system prompt instructs the provider to decline questions that cannot be answered from the supplied context. Basic prompt-injection patterns are blocked before calling the provider.

## Provider Configuration

The provider uses an OpenAI-compatible chat-completions endpoint configured through environment variables:

| Variable | Purpose |
| --- | --- |
| `AI_CHAT_PROVIDER_BASE_URL` | Provider base URL |
| `AI_CHAT_PROVIDER_ENDPOINT` | Chat-completions path; defaults to `/v1/chat/completions` |
| `AI_CHAT_PROVIDER_API_KEY` | Bearer token; chat fails closed when absent |
| `AI_CHAT_PROVIDER_MODEL` | Provider model name |
| `AI_CHAT_RATE_LIMIT_PER_MINUTE` | Successful requests allowed per rolling minute |
| `AI_CHAT_DAILY_LIMIT` | Successful requests allowed per rolling 24 hours |
| `AI_CHAT_TIMEOUT_SECONDS` | Connect and read timeout |

Secrets must be supplied through environment configuration and must not be committed.

## Usage Logging

Every send attempt records an `ai_usage_logs` row in a separate transaction with the Student, course, lesson block, request status, provider, token counts when available, and a sanitized failure category. Prompts, provider errors, and generated answers are not stored.

Statuses:

- `SUCCESS`: provider returned an answer.
- `FAILED`: provider was unavailable or returned an invalid response.
- `BLOCKED`: eligibility, guardrail, or usage-limit rejection.

Flyway migration `V022__add_ai_chat_lesson_block_reference.sql` adds the lesson-block reference. `V021` is reserved for MHB-33 writing-submission compatibility.

## Frontend Flow

For an enrolled AI-supported course, each curriculum row exposes an `Ask AI` action. It opens:

`/student/courses/{courseId}/lesson-blocks/{lessonBlockId}/ai-chat`

The page handles loading, unavailable, retryable error, sending, response, and provider-failure states. The backend remains authoritative if course configuration changes after the page is opened.

## QA Checklist

- Student with active enrollment and eligible course can send a lesson-scoped question.
- Non-enrolled Student sees unavailable and receives 403 when posting directly.
- Course below the configured price floor is unavailable.
- Teacher and internal-admin tokens receive 403; unauthenticated requests receive 401.
- A question about unrelated content is declined by the configured provider.
- Successful, failed, and blocked attempts create sanitized usage-log rows.
- Desktop and mobile layouts expose the curriculum action without overflow.
