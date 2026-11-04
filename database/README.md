# ManabiHub Database

This directory is reserved for database related configurations and migrations (e.g., Flyway scripts, seed data).

## Local Development Setup

We use Docker Compose to run a local PostgreSQL database. The setup provides an isolated, persistent local database for development.

### Connection Details
- **Host**: `localhost`
- **Port**: `5433`
- **Database**: `manabihub`
- **Username**: `manabihub`
- **Password**: `manabihub_dev_password`

> **Note**: These credentials are only for local development. Never use them for production.

### Starting the Database

From the root of the project, run:
```bash
docker compose -f deploy/docker-compose.local.yml up -d
```

### Stopping the Database

To stop the database container without losing data:
```bash
docker compose -f deploy/docker-compose.local.yml down
```

### Resetting the Database

To completely remove the database and its data volume (WARNING: this will delete all local data):
```bash
docker compose -f deploy/docker-compose.local.yml down -v
```

Then start it again and run the backend to re-apply all Flyway migrations:
```bash
docker compose -f deploy/docker-compose.local.yml up -d
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Running Migrations

Flyway migrations are executed automatically when the Spring Boot backend starts. Migration files are located at:

```
backend/src/main/resources/db/migration/
```

| Migration | Description |
|-----------|-------------|
| `V001__init_baseline.sql` | Enables `pgcrypto` extension for UUID generation |
| `V002__create_manabihub_core_schema.sql` | Creates 36 core tables (identity, RBAC, KYC, courses, AI writing, commerce, moderation) |
| `V003__seed_manabihub_core_data.sql` | Seeds roles, permissions, RBAC mappings, system settings, and demo data |

> [!WARNING]
> **Never modify** a migration file once it has been committed and merged. Create a new migration file with the next version number instead.

### pgAdmin (Optional)

An optional pgAdmin service is included in the `docker-compose.local.yml` file. If you'd like a web UI to interact with your database, uncomment the `pgadmin` service in `deploy/docker-compose.local.yml` before starting it. 
It will be available at `http://localhost:5050` with the following credentials:
- **Email**: `admin@manabihub.local`
- **Password**: `admin123`

---

## Demo Admin Accounts (Local Development Only)

> [!CAUTION]
> These accounts use **placeholder password hashes** and are for local development and testing ONLY. Never use these credentials or hashes in production.

| Email | Role | Permissions |
|-------|------|-------------|
| `sysadmin@manabihub.local` | SYSTEM_ADMIN | System config, internal admin management, role assignment, audit logs, AI config |
| `course.manager@manabihub.local` | COURSE_MANAGER | Teacher KYC review, course publication approval, violation/content moderation |
| `finance.manager@manabihub.local` | FINANCE_MANAGER | Refund review, payout execution, financial evidence/reconciliation |

### RBAC Separation

- **Course Manager** does NOT have access to financial operations (refund, payout, finance evidence).
- **Finance Manager** does NOT have access to content operations (KYC, course review, moderation).
- **System Admin** manages configuration, internal accounts, roles, and audit logs.

---

## Demo App Users

| Email | Role | Profile |
|-------|------|---------|
| `student.demo@manabihub.local` | STUDENT | Student profile with JLPT N3 goal |
| `teacher.demo@manabihub.local` | TEACHER | Approved teacher profile (KYC approved, can publish courses) |

---

## AI Writing — Suggestion Only

> [!IMPORTANT]
> **AI Writing is suggestion-only.** AI output is preliminary learning support and is never stored or presented as an authoritative evaluation.

AI Writing features provide **preliminary suggestions** only:
- Grammar suggestions
- Vocabulary suggestions
- Sentence structure suggestions
- Revision guidance

**What AI suggestions are NOT:**
- ❌ Not an official grade or score
- ❌ Not a pass/fail determination
- ❌ Not used for course completion decisions
- ❌ Not used for certificate issuance
- ❌ Not used for refund eligibility decisions

**Only teacher feedback** is considered official assessment. This separation is enforced at the database level:
- `ai_writing_suggestions.is_official` is constrained to `FALSE`
- `teacher_writing_feedback.is_official` is constrained to `TRUE`

---

## Schema Overview

The database consists of **36 tables** organized into 8 functional domains:

| Domain | Tables |
|--------|--------|
| Identity + RBAC | `app_users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `internal_admin_accounts`, `internal_admin_roles` |
| System Settings + Audit | `system_settings`, `audit_logs` |
| Profile + Teacher KYC | `student_profiles`, `teacher_profiles`, `kyc_requests`, `kyc_documents` |
| Course + Content + Approval | `courses`, `course_modules`, `lessons`, `lesson_blocks`, `course_approval_decisions` |
| Enrollment + Learning | `enrollments`, `lesson_progress` |
| AI Writing (Suggestion-Only) | `writing_submissions`, `ai_writing_suggestions`, `teacher_writing_feedback`, `ai_usage_logs` |
| Commerce + Wallet | `orders`, `order_items`, `payment_transactions`, `wallets`, `wallet_transactions`, `escrow_ledger`, `refund_requests`, `withdrawal_requests`, `payout_settlements` |
| Moderation + Notification | `violation_reports`, `moderation_decisions`, `notifications` |
