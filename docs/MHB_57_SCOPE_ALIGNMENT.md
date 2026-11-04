# MHB-57 Council Scope Alignment

Last reviewed: 2026-07-17

## Decisions

- AI Writing is suggestion-only. Its output is preliminary, non-authoritative learning support.
- Teacher feedback is the authoritative record for writing review.
- AI output never determines course completion or refund decisions.
- Quiz and certificate features may continue to use scores where their own business rules require them.
- The Admin Portal supports three internal roles: System Admin, Course Manager, and Finance Manager.

## AI Writing Traceability

| Issue | Responsibility | Aligned behavior |
| --- | --- | --- |
| MHB-30 | Student writing submission | AI assistance returns preliminary revision suggestions only. |
| MHB-31 | Student suggestion and feedback view | AI suggestions and Teacher feedback remain visually and semantically separate. |
| MHB-33 | Teacher writing review | Teacher feedback is authoritative; AI suggestions are optional reference material. |

Related wording was also aligned in MHB-57, MHB-58, and MHB-61. Jira labels identify AI writing work with `ai-suggestion` and `ai-writing` where applicable.

## Admin RBAC Traceability

| Issue | Capability | Authorized internal role |
| --- | --- | --- |
| MHB-8 | Admin Portal login | System Admin, Course Manager, Finance Manager |
| MHB-13 | Teacher KYC review | Course Manager |
| MHB-20 | Course publication approval | Course Manager |
| MHB-40 | Payout settlement | Finance Manager |
| MHB-42 | Refund approval | Finance Manager |
| MHB-44 | Violation moderation | Course Manager |
| MHB-52 | System settings and internal role assignment | System Admin |
| MHB-53 | Audit log viewer | System Admin |

Backend authorization remains the source of truth. Frontend menu visibility is a usability control and must not replace server-side RBAC.
