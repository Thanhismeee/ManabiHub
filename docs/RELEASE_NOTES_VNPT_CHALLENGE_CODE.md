# VNPT eKYC browser build and release contract

## Summary

When `VITE_VNPT_EKYC_ENABLED=true`, the frontend build validates the VNPT Web
SDK configuration below. `npm run build` fails before TypeScript or Vite runs
if a required value is missing, a supplied value still contains a placeholder,
or an unsafe provider/script URL is used.

## Security boundary: every `VITE_*` value is public

Vite replaces `VITE_*` references at build time and writes their values into the
downloadable browser JavaScript. Amplify or GitHub environment variables keep
configuration out of source control and build logs; they **do not make it
secret in the deployed application**.

Only use credentials that VNPT explicitly issues for Web SDK/browser use and
permits to be exposed to end users. Never put a server API key, client secret,
or unrestricted provider credential under a `VITE_*` name. Before release,
VNPT must confirm that the token ID, token key, access token, and any supplied
challenge code are browser-scoped and restricted to the exact deployed origin.
If any value is confidential, the integration must obtain a short-lived
session/token from a server-side adapter instead of embedding it in the frontend.

## Required Amplify branch variables

| Variable | Requirement |
|---|---|
| `VITE_VNPT_EKYC_ENABLED` | Set to `true` only for a provisioned VNPT environment. |
| `VITE_VNPT_EKYC_SDK_SCRIPT_URLS` | Optional. Leave blank for committed SDK assets; any override must be a root-relative same-origin path. Remote scripts are rejected. |
| `VITE_VNPT_EKYC_BACKEND_URL` | Required exact VNPT origin: `https://api.idg.vnpt.vn` or `https://sandbox-idg.vnpt.vn`. Lookalike/custom hosts are rejected. |
| `VITE_VNPT_EKYC_TOKEN_ID` | Required VNPT-approved public browser token identifier. |
| `VITE_VNPT_EKYC_TOKEN_KEY` | Required by the current browser integration. Do not enable it until VNPT confirms this value is safe for public exposure. |
| `VITE_VNPT_EKYC_ACCESS_TOKEN` | Required by the current browser integration and must be browser-scoped. A build-time token needs an expiry and rotation plan. |
| `VITE_VNPT_EKYC_CHALLENGE_CODE` | Optional browser-visible challenge. Leave blank when VNPT has not issued one; bundled Web SDK 3.2.1 then retains its `00000` default. |

Do not mix production and sandbox endpoints or credentials. Changes to any
`VITE_*` value require a new Amplify build because the values are compiled into
the artifact.

If VNPT later issues a challenge code, enter its raw value. The bridge URL-encodes
it exactly once before handing it to the bundled SDK, which concatenates the
value into VNPT API query strings; do not pre-encode it in Amplify. When the
variable is blank or absent, the bridge omits `CHALLENGE_CODE` so the SDK's
`00000` default is not overwritten.

## Provisioning

### AWS Amplify

1. Open Amplify Console → App → the exact deployment branch → Environment variables.
2. Configure all required values above; add the optional challenge only if VNPT issued one.
3. Confirm the VNPT portal allowlists the final HTTPS origin/custom domain.
4. Trigger a clean rebuild and retain the build log as release evidence.
5. Use `npm run build:vnpt-release`; unlike the generic CI build, it fails if
   `VITE_VNPT_EKYC_ENABLED` is missing or false.

There is currently no version-controlled `amplify.yml` in this repository, so
branch scoping and environment propagation remain an external release check.

### GitHub Actions

The normal pull-request CI build intentionally leaves eKYC disabled and must not
receive live VNPT credentials. If a protected release job builds with eKYC
enabled, pass the complete browser configuration through a protected
environment. Treat that mechanism as configuration hygiene, not confidentiality
of the resulting bundle.

### Local development

Copy the names from `frontend/.env.example` into an ignored `frontend/.env.local`.
Use sandbox-issued browser values only. Run:

```text
npm run validate:vnpt-env
npm run build
```

## Demo/UAT versus real production CCCD verification

The checked-in deployment example uses
`KYC_IDENTITY_VERIFICATION_MODE=direct-sdk-mock`. It is a demo/UAT flow that
cross-checks the browser result against synthetic registry data. It is not real
national-ID verification.

An online sandbox may explicitly override that value with
`KYC_IDENTITY_VERIFICATION_MODE=direct-sdk`. In this mode both teacher and
student flows evaluate the real VNPT browser callback, bind its transaction or
`client_session` in the shared replay ledger, fingerprint the returned CCCD,
and mark the sandbox identity verified. That verified state passes the KYC gate
for sandbox withdrawals.

This is an intentional sandbox trust decision: browser callback JSON is
client-controlled evidence, not authoritative server confirmation. Never use
`direct-sdk` with real balances or present it as production identity assurance.
Keep VNPay, wallet, payout, and withdrawal operations in their sandbox modes and
use test accounts only. A stable `KYC_IDENTITY_SECRET` of at least 32 characters
is also required by the identity fingerprinting service.

Real production requires all of the following external/code gates:

- `KYC_IDENTITY_VERIFICATION_MODE=server`;
- a concrete VNPT server-to-server verification adapter (the current fallback
  adapter is deliberately fail-closed);
- VNPT provider server credentials, endpoint, network allowlisting, and rotation;
- server-confirmed transaction/session replay protection for every CCCD flow;
- privacy, retention, incident-response, and vendor-contract approval for CCCD data.

Both entry points now claim provider transactions (or a namespaced
`client_session` fallback when the SDK omits a transaction ID) in one account-level ledger;
the database unique constraint blocks first-claim/replay across student and
teacher flows. The teacher path additionally retains its retry/cooldown state in
the KYC coordinator, while student direct/server verification is rate-limited
per account. Changing the mode variable alone still does not
satisfy the remaining gates. Until the real adapter and provider credentials
exist, the deployment must be labeled demo/UAT.

## Release checks

- Build validation passes with eKYC enabled and the final branch variables.
- The protected artifact is produced with `npm run build:vnpt-release`, not the
  permissive PR/disabled build command.
- The access token remains valid for the planned release window, or is issued
  dynamically by the backend.
- SDK script origins and `connect-src`/CORS policy match the approved VNPT hosts.
- A UAT transaction appears in the correct VNPT partner-portal environment.
- No raw CCCD payload, image, access token, or token key is written to browser
  storage, application logs, build logs, or diagnostics.
- The committed VNPT SDK version, license, and vendor checksum are approved.

If validation fails, the frontend artifact must not be released. At runtime the
bridge also refuses to launch if configuration is unavailable or the configured
JWT already appears expired, but that user-facing guard is not a deployment
substitute.

## Affected files

| File | Change |
|---|---|
| `frontend/.env.example` | Complete public-browser configuration contract. |
| `deploy/.env.production.example` | Separates backend runtime from Amplify build configuration and labels demo/UAT mode. |
| `frontend/scripts/validate-vnpt-env.mjs` | Build-time required-value, exact-origin, same-origin-script, and release-enabled validation. |
| `frontend/package.json` | Runs validation before normal builds and exposes a fail-closed VNPT release build. |
| `frontend/src/features/kyc/vnptIdentitySdk.ts` | Runtime credential validation and optional challenge-code override. |
