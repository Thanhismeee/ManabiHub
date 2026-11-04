# ManabiHub Backend

This is the initial Spring Boot backend skeleton for **ManabiHub** - a Japanese learning marketplace platform.

## Tech Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3.3.1
- **Build Tool:** Maven
- **Database:** PostgreSQL
- **Migrations:** Flyway
- **APIs & Documentation:** Springdoc OpenAPI / Swagger

---

## Directory Structure

```text
backend/
├── pom.xml                               # Maven project configuration
├── README.md                             # Project setup and documentation
├── src/main/java/com/manabihub/
│   ├── ManabiHubApplication.java         # Application main runner
│   ├── common/                           # Cross-cutting concerns (responses, exceptions)
│   │   ├── response/                     # ApiResponse and ErrorResponse structures
│   │   ├── exception/                    # GlobalExceptionHandler and BusinessException
│   │   ├── constants/                    # Constant values and enums
│   │   └── util/                         # Common utility classes
│   ├── security/                         # Security models and configs
│   │   ├── config/                       # Security filter chain and JWT configurations
│   │   ├── jwt/                          # JWT logic and utility files
│   │   ├── oauth/                        # OAuth2 client integration
│   │   └── principal/                    # Security principals / UserDetails
│   └── [domain-packages]/                # Domain modules placeholders (e.g. identity, kyc, course, etc.)
└── src/main/resources/
    ├── application.yml                   # Common configurations
    ├── application-local.yml             # Local environment configurations (database defaults)
    └── db/migration/
        ├── V001__init_baseline.sql                    # Enables pgcrypto extension
        ├── V002__create_manabihub_core_schema.sql     # 36 core tables
        └── V003__seed_manabihub_core_data.sql         # Roles, permissions, RBAC, demo data
```

---

## How to Build and Run

### Prerequisites
1. **Java Development Kit (JDK) 21** installed and configured on your system.
2. **Maven 3.x** installed.
3. **Docker & Docker Compose** installed for the local database.

### Start the Local Database
Before running the backend locally, start the PostgreSQL database container:
```bash
docker compose -f ../deploy/docker-compose.local.yml up -d
```
*(If you are running this from the backend folder. From the project root, drop the `../`)*

### Environment Variables (for local runtime)
When running the application with the `local` profile, the following environment variables are supported with default values:

| Variable Name | Description | Default Value |
| --- | --- | --- |
| `DB_HOST` | Database Hostname | `localhost` |
| `DB_PORT` | Database Port | `5432` |
| `DB_NAME` | Database Name | `manabihub` |
| `DB_USERNAME` | Database Username | `manabihub` |
| `DB_PASSWORD` | Database Password | `manabihub_dev_password` |

### Compile & Test
To build the project and execute automated tests, run:
```bash
mvn clean install
```
*(Tests are configured to bypass database connections during the initial context loading test).*

### Run the Application
To run the Spring Boot application locally with the `local` profile active:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Reset Local Database and Re-run Migrations
To completely reset the database and re-apply all migrations from scratch:
```bash
# From project root
docker compose -f deploy/docker-compose.local.yml down -v
docker compose -f deploy/docker-compose.local.yml up -d

# Wait a few seconds for PostgreSQL to initialize, then run backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Verify Flyway Migration
When the application starts, Flyway will automatically run any pending database migrations.
- Verify in the startup logs that Flyway has executed successfully (e.g., `Successfully validated ...`, `Migrating schema ...`).
- Connect to your local database using `pgAdmin` or `psql` and verify that the `flyway_schema_history` table was created.

> [!WARNING]
> **Important Database Rule**: Never edit or modify a migration file once it has been committed and merged. If you need to change the schema, create a **new** migration file with a higher version number (e.g., `V004__add_new_feature.sql`). Modifying existing scripts will break the Flyway checksum verification for other developers and environments.

---

## Database Schema Overview

The database contains **36 tables** across 8 functional domains. See [`database/README.md`](../database/README.md) for full documentation including:
- Schema domain breakdown
- Demo admin accounts and RBAC matrix
- AI Writing suggestion-only policy
- How to reset and re-run migrations

---

## Verify Endpoints

Once the application has successfully started on port `8080`:

- **Health Checks:**
  - URL: `http://localhost:8080/actuator/health`
  - Returns the health status of the application.
  
- **API Documentation & Swagger UI:**
  - Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
  - OpenAPI Spec JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## API Response Convention

All REST API endpoints follow a standardized response envelope format. For full details on:
- Standard success/error JSON shapes
- Message code naming convention (`DOMAIN_ACTION_OR_STATE`)
- Frontend i18n consumption rules
- Exception handling chain

See **[docs/api-response-convention.md](docs/api-response-convention.md)**.

