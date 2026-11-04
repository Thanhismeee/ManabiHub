# ManabiHub

Japanese learning platform - SEP490 ManabiHub

**[See Local Development Guide (docs/LOCAL_DEVELOPMENT.md)](docs/LOCAL_DEVELOPMENT.md)** for instructions on how to run the project locally using VS Code tasks or PowerShell scripts.

## Local Development

### Prerequisites

- Docker & Docker Compose
- Node.js & npm (for frontend)
- Java 21+ (for backend)

### Starting the Local Database

We use Docker Compose for the local PostgreSQL database. To start it, run:

```bash
docker compose -f deploy/docker-compose.local.yml up -d
```

For more details on the database setup (including stopping, resetting, and connection details), please see the [Database Documentation](database/README.md).

---

## Repository Hygiene

Generated files, local temp folders, dependencies, and secret configs must not be committed. After pulling the repository, enable the shared Git hooks once:

```powershell
.\scripts\install-git-hooks.ps1
```

The pre-commit hook blocks common local-only paths such as `backend/target/`, `frontend/dist/`, `frontend/node_modules/`, `tmp/`, `.env*`, and `application-secrets.yml`. Example templates such as `.env.example` and `.env.local.example` are allowed.

---

## Continuous Integration

CI runs automatically via GitHub Actions (`.github/workflows/ci.yml`).

| Trigger | Condition |
|---------|-----------|
| `pull_request` | Targeting `develop` |
| `push` | To `develop` |

### Jobs

| Job | Command | Environment |
|-----|---------|-------------|
| **Repository Hygiene** | Block generated/local-only files | Ubuntu |
| **Backend Tests** | `./mvnw clean test` | Java 21, Ubuntu |
| **Frontend Build** | `npm ci && npm run build` | Node.js 20, Ubuntu |

Jobs run in parallel. No database is required - backend tests exclude DataSource auto-configuration.

## AWS Deployment

Build the Elastic Beanstalk source bundle with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-eb-bundle.ps1
```

See [docs/AWS_DEPLOYMENT.md](docs/AWS_DEPLOYMENT.md) for the RDS, Beanstalk,
CloudFront, and Amplify configuration.

### Branch Naming

Feature branches must include the Jira issue key:

```text
feature/MHB-<number>-<short-description>
```
