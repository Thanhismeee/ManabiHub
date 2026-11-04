# ManabiHub release candidate handoff

Date: 2026-07-28
Candidate branch: `codex/release-candidate-regression`

## What is in this candidate

This branch integrates the remaining implementation candidates on top of the
latest `develop`, including merged PR #102 (escrow) and PR #109 (KYC/JLPT):

- PR #101: course/payment/payout code-review blockers and payout security;
- PR #103: AI provider contract tests and operations runbook;
- PR #104: public Teacher profile/discovery;
- PR #105: verified course ratings/reviews;
- PR #106: System Settings and internal Admin roles;
- PR #107: frontend dependency, CSV import, and rich-text hardening.

The release branch is the tested combination. PRs #101 and #103-#107 are
superseded by this candidate and must be closed after this branch merges.

## Integration defects found and fixed

1. PR #101 and #106 both changed `WithdrawalServiceImpl`. The candidate keeps
   OTP consumption, encrypted bank data, after-commit notification, wallet
   locking, and the runtime `PAYOUT_THRESHOLD`.
2. PR #105 and #107 both changed the course detail page. The candidate keeps
   the real reviews section and the shared rich-text sanitizer.
3. PR #102 edited applied migration `V003`, which caused a real existing
   database to fail Flyway checksum validation. `V003` is restored; `V039`
   changes only the untouched seven-day escrow default to fourteen days.
4. Known local demo Admin credentials could otherwise be present outside a
   local environment. `V043` disables the accounts by default; a
   `@Profile("local")` initializer creates a BCrypt hash and activates them only
   for local development.

No `flyway repair`, schema reset, or destructive database operation was used.

## Automated evidence

- Backend release candidate: 388/388 Maven tests pass, including PostgreSQL
  Testcontainers, all 43 Flyway migrations, and AI provider contracts.
- Frontend release candidate: 8 test files / 19 tests pass.
- Frontend lint passes.
- Frontend TypeScript and production build pass.
- Dependency audit gate passes with only the documented RSC-only acceptance
  that expires on 2026-08-15.
- Flyway validates the unchanged history and upgrades PostgreSQL through v043.

## Manual local smoke evidence

Using the release candidate frontend on port 15173, backend on port 18081, and
the existing PostgreSQL database:

- public home loaded a real published course;
- course detail loaded curriculum, Teacher link, and the verified-review empty
  state;
- public Teacher profile loaded privacy-safe identity and the published course;
- System Admin login succeeded;
- Admin dashboard exposed only the System Admin menu;
- System Settings loaded all 14 settings and showed the migrated 14-day escrow;
- internal-role management loaded all three active local demo accounts;
- no new browser console error appeared after the corrected login.

Public API checks for categories, courses, and Teachers returned HTTP 200.
Local aggregate health is HTTP 503 only because SMTP credentials are
intentionally absent; the database component is UP. Production disables the
mail health contributor and must be checked through the documented readiness
probe.

## External checks that are still genuinely external

These cannot be marked complete by source tests:

- AWS deployment is still an older version until the team reviews/merges and
  deploys this candidate.
- Live Google OAuth needs the deployed callback/origin configuration.
- Live VNPay needs merchant sandbox credentials and an actual signed IPN.
- Live withdrawal OTP needs SMTP credentials.
- Live AI needs provider base URL, API key, and model. Contract/error handling
  is tested; provider availability and billing are not.

Do not present these as live-verified until the deployment owner attaches
timestamped evidence.

## Code-freeze acceptance

Freeze feature work when:

1. the release-candidate PR is reviewed;
2. CI is green on the final target branch;
3. required AWS variables pass `scripts/check-release-config.ps1`;
4. migrations v038-v043 are visible in deployment logs;
5. the smoke sequence above passes on AWS;
6. the team records the deployed commit SHA and rollback version.

After freeze, accept only a reproducible P0/P1 defect with owner, evidence,
test, rollback, and impact on the defense demo.

## Demo order

1. Public catalog -> course detail -> Teacher profile -> review area.
2. Student purchase/history and one learning block.
3. AI chat or writing suggestion, explicitly described as non-grading.
4. Teacher course checklist/submit/publish and wallet.
5. Finance payout review.
6. System Admin settings and role management.

Do not demo refunds, violation moderation, audit viewer, or Teacher analytics
until their dependent stories are implemented and verified. Align SRS/slides
to that honest MVP scope.

## Rollback

- Keep the previous Elastic Beanstalk application version and Amplify artifact.
- Do not delete or renumber Flyway history.
- Application rollback is safe only when the old code can tolerate schema
  v043; otherwise roll forward with a corrective migration.
- Never rotate `PAYOUT_SECURITY_SECRET` or `KYC_IDENTITY_SECRET` as a rollback.
- If an external provider fails during the defense, use the documented
  unavailable/error state; do not fabricate a successful AI/payment response.
