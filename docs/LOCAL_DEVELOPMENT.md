# Local Development Guide

This guide explains how to run the ManabiHub project locally using the provided development scripts and VS Code tasks.

## Required Tools

Before you begin, ensure you have the following installed and configured in your system PATH:
- **JDK 21**: Required for the Spring Boot backend.
- **Maven (mvn)**: Build tool for the backend.
- **Node.js LTS**: Required for the frontend Vite server.
- **Docker Desktop**: Required to run the local PostgreSQL database.

## Quick Start: VS Code Tasks

If you are using Visual Studio Code (or an Antigravity IDE), the easiest way to manage your local environment is through the built-in tasks.
1. Open the Command Palette (`Ctrl+Shift+P` or `Cmd+Shift+P`).
2. Select **Tasks: Run Task**.
3. Choose one of the following tasks:
   - **ManabiHub: Full Stack**: Starts the database, backend, and frontend all at once.
   - **ManabiHub: DB Up**: Starts the PostgreSQL database.
   - **ManabiHub: DB Down**: Stops the PostgreSQL database.
   - **ManabiHub: DB Reset**: Stops and deletes the local database volume (prompts for confirmation).
   - **ManabiHub: Backend Run**: Starts the Spring Boot backend.
   - **ManabiHub: Frontend Run**: Starts the Vite dev server.

## Running via PowerShell Scripts

If you prefer using the terminal, you can run the PowerShell scripts directly from the root of the project:

### Start Full Stack
```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev-full.ps1
```

### Start Components Individually
**Start Database:**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev-db-up.ps1
```
**Start Backend:**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev-backend.ps1
```
**Start Frontend:**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev-frontend.ps1
```

### Local Admin Portal Accounts

The `local` Spring profile enables three demo-only accounts after Flyway has
finished. They all use `Admin@123` unless `DEMO_ADMIN_PASSWORD` is set before
starting the backend:

| Role | Email |
|---|---|
| System Admin | `sysadmin@manabihub.local` |
| Course Manager | `course.manager@manabihub.local` |
| Finance Manager | `finance.manager@manabihub.local` |

These accounts are disabled by default in the database migration and are
re-enabled only by a `@Profile("local")` initializer. Do not run a shared,
staging, or production environment with the `local` profile.

### Resetting the Database
To wipe the local database and run Flyway migrations from scratch:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev-db-reset.ps1
```
You will be prompted to type `RESET` to confirm. After resetting, run the `dev-db-up.ps1` script and restart the backend.

## Local Database Connection Info

When the database is running via `dev-db-up.ps1`, you can connect to it using the following credentials:
- **Host**: `localhost`
- **Port**: `5433`
- **Database**: `manabihub`
- **Username**: `manabihub`
- **Password**: `manabihub_dev_password`

## Important Notes

- **AI Writing is Suggestion-Only**: AI features in ManabiHub provide preliminary language and revision suggestions. Teacher feedback remains the authoritative learning record, and AI output never determines course completion or refund decisions.
- **Admin Roles**: The system uses three distinct internal roles:
  - `SYSTEM_ADMIN`: System configuration, internal account management, audit logs.
  - `COURSE_MANAGER`: Teacher KYC review, course publication approval, content moderation.
  - `FINANCE_MANAGER`: Refund review, payout execution, financial reconciliation.

## Troubleshooting

- **Docker Desktop not running**: The `dev-db-up.ps1` script will fail if Docker is not started. Ensure Docker Desktop is open and the Docker engine is running.
- **Port 5433 already in use**: If another local service is using port 5433, either stop that service or run the database with a different `POSTGRES_PORT` value and set `DB_PORT` for the backend to match.
- **Java version is not 21**: The `dev-backend.ps1` script will show a warning if your default `java -version` is not 21. Ensure JDK 21 is set in your `JAVA_HOME` and PATH.
- **Maven not found**: Ensure Maven is installed and its `bin` directory is in your system PATH.
- **PostgreSQL health check timeout**: If your machine is slow, the health check might time out. The script will show a warning but continue. You can check the container status manually using `docker compose -f deploy/docker-compose.local.yml ps`.
