# Internal Admin Authentication Operations

## Scope

This runbook covers the separate ManabiHub Internal Admin Portal authentication
used by `SYSTEM_ADMIN`, `COURSE_MANAGER`, and `FINANCE_MANAGER`.

Public Google OAuth accounts and internal administrator accounts remain separate.
Never add an internal role to a public OAuth token or send an administrator
password by email.

## Session Model

- Access JWT: 15 minutes, held only in page memory. It is never persisted in
  `localStorage` or `sessionStorage`.
- Refresh token: random 256-bit value, stored only in a scoped `HttpOnly` cookie.
- Remember me disabled: browser-session cookie, 12-hour absolute session.
- Remember me enabled: 30-day absolute session and 7-day sliding idle timeout.
- Refresh tokens rotate on every use. Reuse of a rotated token revokes the full
  session family.
- Logout revokes the server session before clearing browser state.
- Role changes, account disabling, invitation acceptance, password reset, and
  password change increment `credential_version`, so stale JWTs stop working
  immediately. Server-side session cleanup runs asynchronously after commit;
  cleanup failure cannot make an old JWT valid again.
- Password-reset links are one-time, expire after 30 minutes, and are delivered
  in a URL fragment so reverse proxies and server logs do not receive the token.
- Requesting a reset does not invalidate the current password. A successful
  reset changes the password and revokes every active session.
- Reset-request issuance and email delivery run asynchronously, keeping known
  and unknown account responses indistinguishable at the HTTP boundary.
- Forgot-password admission is limited synchronously by IP first and then email.
  Accepted work uses a dedicated bounded executor (2 core threads, 4 maximum,
  queue capacity 100); saturation drops work while preserving the generic HTTP
  response and never falls back to the request thread.
- Invalid current-password attempts are limited by account and IP buckets:
  5 account attempts or 15 IP attempts in 15 minutes trigger a 30-minute block.

## Elastic Beanstalk Environment

Set these as Elastic Beanstalk environment properties. Do not commit values.

```text
ADMIN_ACCESS_TOKEN_MINUTES=15
ADMIN_SESSION_HOURS=12
ADMIN_REMEMBER_SESSION_DAYS=30
ADMIN_REMEMBER_IDLE_DAYS=7
ADMIN_PASSWORD_RESET_MINUTES=30
ADMIN_PASSWORD_RESET_COOLDOWN_SECONDS=60
ADMIN_COOKIE_SECURE=true
ADMIN_COOKIE_SAME_SITE=None
FRONTEND_BASE_URL=https://<amplify-develop-domain>
CORS_ALLOWED_ORIGINS=https://<amplify-develop-domain>
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<sender-address>
MAIL_PASSWORD=<app-password-or-provider-secret>
```

Production startup fails when `CORS_ALLOWED_ORIGINS=*`. List exact trusted
origins, comma-separated, when more than one frontend is intentionally supported.

`SameSite=None` is required while Amplify and the API use different sites, and it
is rejected at startup unless `ADMIN_COOKIE_SECURE=true`.

For a long-lived production service, prefer Amazon SES or another transactional
provider over a personal Gmail account. The current credential-email path sends
asynchronously after the database transaction commits. If delivery fails, the undelivered
invitation/reset token is revoked and an audit event is stored, allowing a System
Admin to resend safely.

## API Gateway and Amplify

- Amplify must keep `VITE_API_BASE_URL=https://<api-host>/api`.
- API Gateway or the reverse proxy must preserve `Set-Cookie` response headers.
- CORS preflight must allow credentials and the `X-Admin-CSRF` header.
- Never cache `/api/admin/auth/**` responses.
- Keep the cookie path `/api/admin/auth`; changing the public API prefix requires
  changing `AdminRefreshCookieService` and its tests together.
- Amplify rewrites must send SPA routes such as `/admin/reset-password` to
  `index.html` without rewriting API calls.

## Database Migration

Flyway migration `V049__secure_internal_admin_auth_lifecycle.sql` adds:

- `credential_version` on internal admin accounts;
- server-side admin sessions;
- refresh-token families;
- one-time password reset records;
- database-backed authentication rate-limit buckets.

Deploy the backend before deploying the frontend. Do not edit an applied Flyway
migration; add a new migration for future changes.

## Release Smoke Test

1. Sign in without Remember me and verify the refresh cookie has `HttpOnly`,
   `Secure`, `SameSite=None`, no persistent `Max-Age`, and path
   `/api/admin/auth`.
2. Reload an admin page; the in-memory access token should be restored through
   the refresh cookie without placing the new token in browser storage.
3. Sign in with Remember me and verify a persistent cookie is issued.
4. Open two tabs, let the access token refresh, and confirm both tabs remain
   usable.
5. Log out in one tab. Protected requests in all tabs must return to admin login.
6. Request password reset for a known and unknown email. Both HTTP responses must
   be indistinguishable.
7. Complete a reset once. Reusing the same link must fail and all old sessions
   must be rejected.
8. Change an account role or disable it. Its existing access JWT must receive
   `ADMIN_SESSION_STALE`.
9. Remove SMTP access temporarily. The reset/invitation token must be revoked and
   the corresponding delivery-failure audit event must exist.
10. Confirm no raw password, refresh token, reset token, or invitation token
    appears in application logs or database audit metadata.
