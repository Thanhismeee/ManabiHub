# AWS Deployment

This setup deploys the Spring Boot API to Elastic Beanstalk, PostgreSQL to
RDS/Aurora, and the React application to Amplify.

## 1. Build the Elastic Beanstalk bundle

From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-eb-bundle.ps1
```

The command runs backend tests and creates:

```text
backend/target/manabihub-elastic-beanstalk.zip
```

The ZIP root must contain exactly the executable JAR and `Procfile`. Do not zip
the `backend` or `target` directory itself.

## 2. Elastic Beanstalk environment properties

Configure these under **Configuration > Updates, monitoring, and logging >
Environment properties**:

```text
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=5000
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/postgres?sslmode=require
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
RDS_USERNAME=<database user>
RDS_PASSWORD=<database password>
GOOGLE_CLIENT_ID=<Google OAuth client id>
GOOGLE_CLIENT_SECRET=<Google OAuth client secret>
JWT_SECRET=<random value with at least 32 bytes>
KYC_IDENTITY_SECRET=<stable random value with at least 32 bytes>
PAYOUT_SECURITY_SECRET=<stable random value with at least 32 bytes>
MAIL_USERNAME=<SMTP username used for OTP and internal-admin invitations>
MAIL_PASSWORD=<SMTP app password>
VNPAY_TMN_CODE=<VNPay merchant terminal code>
VNPAY_HASH_SECRET=<VNPay merchant hash secret>
VNPAY_RETURN_URL=https://develop.d1sbjmyazduh3v.amplifyapp.com/checkout/return
AI_CHAT_PROVIDER_BASE_URL=<OpenAI-compatible provider origin>
AI_CHAT_PROVIDER_API_KEY=<provider API key>
AI_CHAT_PROVIDER_MODEL=<provider-supported model>
FRONTEND_BASE_URL=https://develop.d1sbjmyazduh3v.amplifyapp.com
CORS_ALLOWED_ORIGINS=https://develop.d1sbjmyazduh3v.amplifyapp.com
PHONE_VERIFICATION_SMS_MODE=firebase
WITHDRAWAL_OTP_MODE=firebase
FIREBASE_PHONE_AUTH_ENABLED=true
FIREBASE_PROJECT_ID=<Firebase project id>
FIREBASE_SERVICE_ACCOUNT_JSON_BASE64=<AWS Secrets Manager source; never plain text>
```

For the `prod` profile, `VNPAY_RETURN_URL` is mandatory and the backend fails
startup unless it is an absolute HTTPS URL on the same origin as
`FRONTEND_BASE_URL`, with the exact path `/checkout/return` and no query string
or fragment. Register that browser return URL in the VNPay merchant portal.
Register the public server-to-server callback separately as:

```text
https://<api-domain>/api/v1/payments/vnpay/ipn
```

The browser return page only polls order status; the signed IPN remains the
authoritative payment confirmation. A copy-safe non-secret variable template is
available at `deploy/.env.production.example`.

Never commit real passwords, JWT secrets, or OAuth secrets to Git.

### Firebase real-SMS verification

This release uses Firebase only as a fresh proof that the signed-in ManabiHub
user controls the configured phone number. ManabiHub JWT remains the application
login. The backend creates a one-time challenge, verifies the Firebase ID token,
checks the exact phone number and recent `auth_time`, and deletes the challenge
after one successful profile verification or withdrawal request. It never stores
the SMS code or Firebase ID token.

Configure one Firebase project as follows:

1. Check the quota shown under **Authentication > Sign-in method**. The
   `manabihub-883a0` Spark project currently receives 10 real verification SMS
   per day, which is sufficient for a controlled demo. Upgrade to **Blaze** only
   when a larger quota is required; before upgrading, set a Google Cloud budget
   and quota alerts.
2. Under **Authentication > Sign-in method**, enable **Phone**.
3. Under **Authentication > Settings > SMS region policy**, allow Vietnam. New
   projects may initially allow no SMS regions.
4. Add `develop.d1sbjmyazduh3v.amplifyapp.com` under **Authorized domains**.
5. Register a Web app and copy only its public Web configuration to these
   Amplify branch variables:

```text
VITE_FIREBASE_PHONE_AUTH_ENABLED=true
VITE_FIREBASE_API_KEY=<Firebase Web API key>
VITE_FIREBASE_AUTH_DOMAIN=<project-id>.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=<project-id>
VITE_FIREBASE_APP_ID=<Firebase Web app id>
VITE_FIREBASE_MESSAGING_SENDER_ID=<numeric sender id>
```

Do not configure fictional/test phone numbers for the deployed demo, and never
set `appVerificationDisabledForTesting`. The browser uses Firebase's invisible
reCAPTCHA and `signInWithPhoneNumber`, so a successful request sends a billable
SMS to the real handset. Firebase sends/stores the supplied phone number for
anti-abuse processing; the UI discloses this before the first verification.

For backend token verification, generate a Firebase Admin service-account JSON
and Base64-encode the entire file. If the encoded value is at most 4,096 bytes,
store it as an encrypted AWS Systems Manager Parameter Store `SecureString`
(Standard tier). On the current Corretto 25 AL2023 platform, configure
`FIREBASE_SERVICE_ACCOUNT_JSON_BASE64` with **Source = Parameter Store**, not
Plain text. Grant the Elastic Beanstalk EC2 instance profile `ssm:GetParameter`
only for that parameter ARN. If the encoded value exceeds the Parameter Store
Standard limit, use one AWS Secrets Manager secret and the equally scoped
`secretsmanager:GetSecretValue` permission instead. Both approaches keep the
private key out of the combined 4,096-byte plain-property limit.

The remaining backend values are ordinary non-secret environment properties:

```text
PHONE_VERIFICATION_SMS_MODE=firebase
WITHDRAWAL_OTP_MODE=firebase
FIREBASE_PHONE_AUTH_ENABLED=true
FIREBASE_PROJECT_ID=<same project id as the Web app and service account>
```

When the AWS parameter or secret is rotated, refresh/apply the Elastic Beanstalk
environment configuration so instances receive the new value. Never place the service-account
JSON, Base64 value, private key, Firebase ID token, or SMS code in Git, Amplify,
screenshots, Jira, logs, or a `VITE_*` variable.

Redeploy Amplify after adding the Firebase Web variables. Then deploy the backend
bundle so Flyway applies `V081`. Smoke-test in this order: verify one previously
unverified profile with a real Vietnamese number; reload and confirm it is locked;
request one withdrawal; receive a new SMS; submit it once; retry the same proof and
confirm the backend rejects it.

Official setup references: [Firebase phone authentication](https://firebase.google.com/docs/auth/web/phone-auth),
[Firebase Authentication limits](https://firebase.google.com/docs/auth/limits),
[Firebase Admin setup](https://firebase.google.com/docs/admin/setup), and
[Elastic Beanstalk environment secrets](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/AWSHowTo.secrets.env-vars.html).

### Legacy eSMS fallback

The eSMS API key and SecretKey are backend credentials. Store them only as
Elastic Beanstalk environment properties; never expose them through Amplify or
any `VITE_*` variable. Obtain them from **eSMS > Quan ly API**. The VNPT eKYC
Token ID/Token Key/Access Token cannot be reused for SMS.

ManabiHub sends its own six-digit OTP through the eSMS OTP/CSKH endpoint with
`SmsType=2`. Before setting `PHONE_VERIFICATION_ESMS_SANDBOX=0`, ask eSMS to
activate the configured Brandname and register a template whose delivered text
matches the following pattern (only the six-digit value changes):

```text
Ma xac thuc ManabiHub cua ban la <OTP 6 digits>. Ma co hieu luc trong 5 phut.
```

The placeholder above is illustrative, not a value to paste into the eSMS
portal. Copy the exact variable syntax and approved `TempContent` returned by
eSMS; the application sends the actual six digits in the API `Content` field.

`PHONE_VERIFICATION_ESMS_SANDBOX=1` validates the request but does not deliver
an SMS or charge the account. A real handset smoke requires `0`, sufficient
balance, an active Brandname, and the approved template. Provider response code
`100` means eSMS accepted the request; it is not a delivery receipt.

After deployment, sign in as one test student or teacher, open the profile,
request one code, enter the six digits, confirm the phone becomes locked, and
reload once to verify the state persists. Do not paste credentials, OTPs, or
provider request bodies into tickets, screenshots, chat, or logs.

`PAYOUT_SECURITY_SECRET` is not supplied by VNPay, AWS, or a bank. Generate it
once for ManabiHub and store it as an Elastic Beanstalk environment property.
Generate `KYC_IDENTITY_SECRET` separately; do not reuse the same value:

```powershell
$secretBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
[Convert]::ToBase64String($secretBytes)
```

Run the command twice and copy each output directly into the corresponding
environment property. The command prints a newly generated secret, so do not
paste its output into chat, Jira, source code, screenshots, or logs.

Keep both secrets stable:

- changing `PAYOUT_SECURITY_SECRET` without a key-rotation migration makes
  existing encrypted bank-account numbers unreadable and changes OTP/account
  fingerprints;
- changing `KYC_IDENTITY_SECRET` changes identity fingerprints and can break
  duplicate-identity detection;
- changing `JWT_SECRET` invalidates active sessions, which is normally safe but
  should still be scheduled.

Before deployment, load the intended environment values in a secure terminal
and run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-release-config.ps1
```

The script reports only presence and length, never the secret values. AI
provider behavior and the live smoke procedure are documented in
`docs/AI_PROVIDER_RUNBOOK.md`.

Set the environment health check path to:

```text
/actuator/health/readiness
```

## 3. RDS network and authentication

- Prefer keeping Elastic Beanstalk and RDS in the same region and VPC.
- For a VPC database, allow PostgreSQL TCP `5432` from the Elastic Beanstalk
  EC2 security group instead of `0.0.0.0/0`.
- If the Aurora Internet access gateway is used, connect through its public RDS
  endpoint and set `iamHost` to the writer endpoint shown by the RDS console.
- IAM authentication requires an AWS RDS hostname and TLS (`sslmode=require`).

Flyway owns schema changes. The production profile uses
`spring.jpa.hibernate.ddl-auto=validate`; do not switch it back to `update`.

## 4. HTTPS between Amplify and the API

Amplify is HTTPS, while a single-instance Elastic Beanstalk environment exposes
HTTP by default. Browsers block direct HTTPS-to-HTTP API calls.

Put an API Gateway HTTP API in front of Elastic Beanstalk:

- Route: `ANY /{proxy+}`.
- Integration URL:
  `http://<elastic-beanstalk-domain>/{proxy}`.
- Stage: `$default` with auto-deploy enabled.
- Keep backend CORS allowed origins pointed at the Amplify domain.

Then configure the Amplify `develop` branch variable:

```text
VITE_API_BASE_URL=https://<api-gateway-id>.execute-api.<region>.amazonaws.com/api
```

Redeploy Amplify after changing a `VITE_*` variable because Vite embeds it at
build time. Do not use `https://example.com/api`.

## 5. Smoke checks

```powershell
curl.exe -i https://<api-domain>/actuator/health/readiness
curl.exe -i https://<api-domain>/api/v1/course-categories
```

Expected readiness response:

```json
{"status":"UP"}
```

If nginx returns `502`, inspect `/var/log/web.stdout.log` and
`/var/log/nginx/error.log` from the Elastic Beanstalk full logs. The common
causes are a missing production environment property, failed RDS connectivity,
or an incorrectly structured source bundle.

The three `@manabihub.local` demo administrators are disabled by migration in
every environment. Only the Spring `local` profile re-enables them. Never set a
production deployment to the `local` profile.

### Bootstrap the first production administrator

Production must never reuse the known local demo password. If the database has
no active `SYSTEM_ADMIN`, configure these Elastic Beanstalk environment
properties before deploying:

```text
ADMIN_BOOTSTRAP_EMAIL=<real administrator email>
ADMIN_BOOTSTRAP_PASSWORD=<20-72 byte strong random password>
ADMIN_BOOTSTRAP_FULL_NAME=<administrator display name>
```

The password must contain uppercase, lowercase, digit, and special characters
without whitespace. On startup, the application takes a PostgreSQL advisory
lock and creates or reactivates exactly one `SYSTEM_ADMIN`. The operation is
audited without storing the credential.

After the administrator has successfully logged in, remove all three bootstrap
properties from Elastic Beanstalk. Later restarts do not reset the password
when an active `SYSTEM_ADMIN` already exists. If every system administrator is
disabled in the future, startup fails closed until a deliberate bootstrap
credential is configured again.

### Invite later internal administrators

The bootstrap exists only to establish the first `SYSTEM_ADMIN`. Do not add
Course Manager or Finance Manager credentials as environment properties.
After the first login, use **Admin Portal > Internal accounts** to invite each
operator with exactly one role.

An invited account remains `DISABLED` until the recipient follows the
single-use password setup link. The backend stores only a SHA-256 token hash,
expires invitations after `ADMIN_INVITATION_TTL_HOURS` (24 hours by default),
and never sends a temporary password.

Before creating an invitation, configure and verify:

```text
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<SMTP account>
MAIL_PASSWORD=<SMTP app password or provider credential>
FRONTEND_BASE_URL=https://develop.d1sbjmyazduh3v.amplifyapp.com
ADMIN_INVITATION_TTL_HOURS=24
```

The invitation is persisted before email delivery is queued. If the mail
provider rejects delivery, fix SMTP configuration and use **Send again** in
the Admin Portal; this revokes every older open link for that account.
