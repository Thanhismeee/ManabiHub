# ManabiHub AI Provider Runbook

## What the AI features do

ManabiHub has two provider-backed learning-support features:

1. **Lesson AI chat (UC-16 / MHB-32)** answers a Student question using one
   allowed lesson block and public-safe course metadata.
2. **AI writing assistance (UC-14 / MHB-30)** returns preliminary grammar,
   vocabulary, structure, and revision suggestions in a strict JSON shape.

AI output is never an official score, pass/fail result, course-completion
decision, or Teacher feedback.

## Required production configuration

The backend reads an OpenAI-compatible chat-completions provider from environment
variables.

| Variable | Required | Meaning |
| --- | --- | --- |
| `AI_CHAT_PROVIDER_BASE_URL` | Yes | Provider origin, without the endpoint path |
| `AI_CHAT_PROVIDER_API_KEY` | Yes | Provider bearer token; never commit or print it |
| `AI_CHAT_PROVIDER_MODEL` | Yes | A chat model supported by that provider |
| `AI_CHAT_PROVIDER_ENDPOINT` | No | Defaults to `/v1/chat/completions` |
| `AI_CHAT_RATE_LIMIT_PER_MINUTE` | No | Defaults to `10` successful requests |
| `AI_CHAT_DAILY_LIMIT` | No | Defaults to `50` successful requests |
| `AI_CHAT_TIMEOUT_SECONDS` | No | Defaults to `20` seconds |

The feature also fails closed unless these database-backed system settings are
valid:

| Setting | Required value for the flow |
| --- | --- |
| `AI_ENABLED` | `true` |
| `AI_CHATBOT_ENABLED` | `true` for lesson chat |
| `AI_WRITING_ENABLED` | `true` for writing suggestions |
| `AI_SUPPORT_PRICE_FLOOR` | Course price must be greater than or equal to this value |

The course must have `ai_supported = true`, and the Student must have an ACTIVE
enrollment.

## Safe configuration check

Run:

```powershell
.\scripts\check-ai-config.ps1
```

The script reports only whether each environment variable exists and its
character count. It never prints the secret value.

## What automated tests prove

The backend suite covers:

- Student-only authorization and request validation.
- Enrollment, course AI support, feature flags, and price-floor eligibility.
- Per-minute and daily limit responses.
- Current-lesson context isolation and quiz-answer exclusion.
- Sanitized SUCCESS, BLOCKED, and FAILED usage logs.
- Provider request authentication, endpoint, model, and payload contract.
- Chat response and token-usage parsing.
- Writing `json_object` request and required suggestion-array schema.
- Fail-closed behavior for missing configuration, empty answers, invalid JSON,
  invalid schema, and provider failure.
- Writing submission persistence when the provider fails.

These tests use a local HTTP contract server. They prove the application/provider
protocol without consuming a production API key.

## What still requires deployed smoke evidence

Automated tests cannot prove that a real production key, provider account, model,
network route, or provider quota is valid. After deploying the current build:

1. Verify the Elastic Beanstalk environment has every required variable.
2. Verify the database settings above.
3. Sign in as a Student with an ACTIVE enrollment in an AI-supported course.
4. Open one lesson and use **Ask AI**.
5. Ask one question answerable from that lesson and one unrelated question.
6. Submit a Writing block and request AI writing assistance.
7. Confirm the Student submission remains stored if the provider is made
   unavailable.
8. Confirm `ai_usage_logs` has sanitized SUCCESS/BLOCKED/FAILED records and does
   not contain prompts, answers, raw provider errors, or secrets.

## Failure interpretation

| User-visible result | Likely cause | Check |
| --- | --- | --- |
| AI unavailable for this course | Feature flag, enrollment, price floor, or `ai_supported` | System settings and course/enrollment rows |
| Too many requests | Minute/daily usage limit | Recent successful `ai_usage_logs` |
| AI temporarily unavailable | Missing/invalid key, model, endpoint, timeout, provider outage, or invalid response | Environment presence, provider account, backend logs |
| Writing suggestion failed | Provider/JSON/schema failure | Submission is retained; inspect sanitized status and backend logs |

## Operational rules

- Never paste the API key into Git, Jira, PRs, screenshots, logs, or chat.
- Store the key in the deployment secret/environment configuration.
- Rotate a key immediately if it has been exposed.
- Keep all backend instances on the same provider configuration during rollout.
- A green automated suite is necessary but does not replace one live deployed
  success test.
