# Admin Operations (Read-only)

## Purpose

`/admin/operations` gives a `SYSTEM_ADMIN` a compact diagnostic view without
granting deployment or infrastructure control. It is intended for incident
triage and configuration-presence checks, not as a replacement for AWS,
CloudWatch, or the immutable Audit Log.

## Access and API contract

The backend exposes only authenticated `GET` endpoints under
`/api/v1/admin/operations`:

- `/overview`: application, database, profile, build, and instance summary;
- `/runtime-config`: fixed component names with configured/enabled booleans;
- `/logs`: bounded recent-process logs with level, query, correlation ID, and
  limit filters.

Every endpoint requires `ROLE_SYSTEM_ADMIN`. The frontend route and navigation
item apply the same role restriction, but backend authorization remains the
security boundary.

## Security boundaries

- No raw environment variable, Spring property, credential, or secret value is
  returned.
- There are no write, restart, deploy, shell, AWS-control, or environment
  download actions.
- Runtime configuration uses an explicit allowlist of component labels and
  boolean states.
- Log messages and exceptions are length-bounded and sanitized before storage
  and again before output. Tokens, credentials, OTPs, contact details, and
  identity/payment-like values are redacted.
- The in-memory log buffer has bounded entry and byte limits. It captures
  application `INFO`, `WARN`, and `ERROR` events and selected external errors.
- Browser rendering treats log content as text and applies defensive redaction.

## Log limitations

The displayed log is only a diagnostic window for the current backend process.
It is cleared by restart, replacement, or deployment, may differ between
instances, and is not a complete history. Use the Admin Audit Log for business
actions and AWS logs for infrastructure/runtime investigation.

## Release verification

The pull request must pass every job in `.github/workflows/ci.yml`: repository
hygiene, the clean backend test suite, and frontend dependency audit, lint,
tests, and production build. A mergeable PR with no recorded checks is not a
successful CI result.

After deploying backend before frontend:

1. Verify `/actuator/health/readiness` returns `UP`.
2. Verify an unauthenticated operations request is rejected.
3. Sign in as a `SYSTEM_ADMIN` and open `/admin/operations`.
4. Confirm overview and allowlisted configuration states load without raw
   values.
5. Generate a harmless application event and confirm the recent-log view can
   filter it without exposing request credentials or personal data.
6. Confirm a non-system administrator cannot access either the route or API.

Do not enable CloudWatch streaming, change retention, or add infrastructure
permissions as part of this feature without a separate reviewed decision.
