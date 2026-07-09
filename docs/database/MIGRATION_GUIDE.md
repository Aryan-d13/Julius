# Julius — Database Migration Guide

## Local Development Setup

### Prerequisites

- Docker Desktop (for PostgreSQL and Redis)
- Java 21
- Maven 3.9+

### First-Time Setup

```bash
# 1. Start infrastructure
make up

# 2. Run the application (Flyway migrates automatically)
make dev
```

On first startup, Flyway will:
1. Create the `flyway_schema_history` tracking table
2. Execute `V1__init_schema.sql` (7 tables, indexes, constraints)
3. Execute `R__seed_dev_data.sql` (dev user + sample data)
4. Hibernate validates the schema against JPA entities

### Daily Development

```bash
make dev            # Start infra + Spring Boot
make test           # Fast unit tests (H2, no Docker)
make test-all       # All tests (unit + integration)
make validate       # Validate migrations against PostgreSQL
make info           # Show migration status
```

### Reset Database

```bash
make db-reset       # Drop all tables + re-migrate from scratch
```

## Running Tests

### Unit Tests (H2 — Fast, No Docker)

```bash
make test
# or: mvn clean test -DexcludedGroups=integration
```

Uses H2 in PostgreSQL compatibility mode. Flyway runs the same migrations against H2.

### Integration Tests (PostgreSQL Testcontainers)

```bash
make test-integration
# or: mvn clean test -Dgroups=integration
```

Spins up a real PostgreSQL 16 container via Testcontainers. Validates:
- Flyway migration on real PostgreSQL
- Hibernate schema validation
- CRUD operations
- `FOR UPDATE SKIP LOCKED` concurrency behavior
- Health endpoint

**Requires Docker to be running.**

## CI Pipeline

### Recommended CI Steps

```yaml
# 1. Unit tests (fast, no Docker)
- run: mvn clean test -DexcludedGroups=integration

# 2. Integration tests (requires Docker-in-Docker or Docker service)
- run: mvn clean test -Dgroups=integration

# 3. Flyway validation (requires PostgreSQL service)
- run: mvn flyway:validate
```

### Flyway Validate in CI

The `mvn flyway:validate` command checks that:
- All migration files have valid SQL
- No previously applied migration has been modified (checksum verification)
- Migration versions are sequential

This should run **before deployment** to catch migration issues early.

## Production Deployment

### Connecting to Production PostgreSQL

Set environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-host:5432/julius
export SPRING_DATASOURCE_USERNAME=julius_app
export SPRING_DATASOURCE_PASSWORD=<secret>
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
```

Or use Spring profile properties:

```properties
spring.datasource.url=jdbc:postgresql://prod-host:5432/julius
spring.datasource.username=julius_app
spring.datasource.password=${DB_PASSWORD}
```

### First Production Deployment

1. Ensure the PostgreSQL database exists and is empty
2. Deploy the application
3. Flyway automatically creates `flyway_schema_history` and runs all migrations
4. Hibernate validates the schema
5. Application starts serving traffic

### Baselining an Existing Database

If migrating from a database where Hibernate `ddl-auto=update` previously created the tables:

```bash
mvn flyway:baseline -Dflyway.baselineVersion=1
```

This marks V1 as already applied without executing it.

## Recovery Procedures

### Strategy: Forward-Only Migrations + Backups

Julius uses a forward-only migration strategy:
- **No undo migrations.** Every migration moves the schema forward.
- **Recovery** is achieved through database backups and application-level rollback.
- **Breaking changes** require a new migration that reverses the previous change.

### If a Migration Fails on Startup

Flyway runs migrations inside a transaction (on PostgreSQL). If a migration fails:
1. PostgreSQL automatically rolls back the failed transaction
2. The `flyway_schema_history` table will NOT contain the failed migration entry
3. Fix the migration SQL
4. Redeploy — Flyway will retry the migration

### If You Need to Roll Back a Deployed Migration

1. **Take a database backup before every deployment:**
   ```bash
   pg_dump -h prod-host -U julius_app -d julius > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **If rollback is needed, restore from backup:**
   ```bash
   psql -h prod-host -U julius_app -d julius < backup_YYYYMMDD_HHMMSS.sql
   ```

3. **Deploy the previous application version** (which expects the old schema)

### If You Need to Fix a Bad Migration

1. Create a new versioned migration that reverses the changes:
   ```
   V3__revert_bad_column.sql
   ```
   ```sql
   ALTER TABLE jobs DROP COLUMN IF EXISTS bad_column;
   ```

2. Deploy with both the fix migration and application code changes

### Emergency: Drop and Re-Migrate (Development Only)

```bash
make db-reset
```

> **WARNING:** This destroys all data. Never run in production.

## Troubleshooting

### "Validate failed: Migrations have been applied that are not resolved locally"

A migration was applied in the database but the SQL file is missing from the codebase. Either:
- Restore the missing migration file
- Or repair the Flyway history: `mvn flyway:repair`

### "Schema-validation: wrong column type"

Hibernate `validate` detected a mismatch between a JPA entity and the Flyway-created column. Check:
1. Column type in migration SQL vs `@Column` annotation
2. Column length (default is 255 for `String`)
3. Nullability (`int` primitives are always `NOT NULL`)

### "Flyway detected resolved migration not applied to database"

A new migration file exists but hasn't been applied. Run:
```bash
make migrate    # Local
mvn flyway:migrate  # CI/Production
```

Or restart the application — Flyway auto-migrates at startup.
