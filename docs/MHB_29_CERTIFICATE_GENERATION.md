# MHB-29 Certificate Generation

## Issuance rules

Certificate issuance reuses the MHB-26 `CertificateEligibilityService`. The server
re-evaluates every rule inside the issuance transaction and never trusts a frontend
eligibility flag.

An enrollment is eligible only when:

1. Course progress is 100 percent.
2. Required Writing blocks are complete.
3. The average best Quiz score is at least 85 percent, when Quiz blocks exist.
4. The Final Test has a passing attempt.

## Persistence and idempotency

- `learning_certificates` stores one immutable certificate per enrollment.
- Database unique constraints protect both `enrollment_id` and `certificate_number`.
- Generation locks the enrollment row before checking for an existing certificate.
- Repeated generation requests return the existing certificate.
- The record snapshots the student name, course title, eligibility result, and issue time.

## Access and download

Both read and generate endpoints resolve the current student from the JWT and then find
that student's enrollment. They do not accept a student or enrollment identifier.

The learning page renders the issued record and downloads a self-contained printable
HTML certificate. A server-rendered PDF can be added later without changing the
eligibility or persistence contract.
