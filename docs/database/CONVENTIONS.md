# Julius — Database Migration Conventions

## Flyway Configuration

- **Migration location:** `src/main/resources/db/migration/`
- **Dialect:** ANSI SQL compatible with both PostgreSQL 16 and H2 (`MODE=PostgreSQL`)
- **Strategy:** Forward-only migrations. No undo scripts. Production recovery via backups + application rollback.
- **Hibernate mode:** `validate` — Hibernate checks schema at startup but never modifies it.

## File Naming

### Versioned Migrations

```
V{version}__{description}.sql
```

| Part | Rule | Example |
|---|---|---|
| `V` | Prefix (required) | `V` |
| `{version}` | Sequential integer | `1`, `2`, `3` |
| `__` | Double underscore separator | `__` |
| `{description}` | Snake_case description | `init_schema`, `add_billing_tables` |

Examples:
- `V1__init_schema.sql`
- `V2__add_billing_tables.sql`
- `V3__add_job_priority_column.sql`

### Repeatable Migrations

```
R__{description}.sql
```

Re-executed whenever the file checksum changes. Used for:
- Dev seed data
- Views
- Stored procedures (if any)

Examples:
- `R__seed_dev_data.sql`

## SQL Standards

### Naming

| Object | Convention | Example |
|---|---|---|
| Table | Plural, snake_case | `job_clips` |
| Column | Singular, snake_case | `created_at` |
| Primary key constraint | `pk_{table}` | `pk_jobs` |
| Unique constraint | `uq_{table}_{columns}` | `uq_jobs_idempotency_key` |
| Index | `idx_{table}_{columns}` | `idx_jobs_status` |
| Foreign key constraint | `fk_{table}_{ref_table}` | `fk_jobs_users` |

### Type Mappings

Use these SQL types to maintain PostgreSQL + H2 compatibility:

| Java Type | SQL Type |
|---|---|
| `String` (default) | `VARCHAR(255)` |
| `String` (with length) | `VARCHAR(n)` |
| `String` (text/JSON) | `TEXT` |
| `int` / `Integer` | `INTEGER` |
| `long` / `Long` | `BIGINT` |
| `double` / `Double` | `DOUBLE PRECISION` |
| `boolean` | `BOOLEAN` |
| `LocalDateTime` | `TIMESTAMP` |
| `@Enumerated(STRING)` | `VARCHAR(255)` |
| UUID (as String) | `VARCHAR(36)` |

### Rules

1. **Every migration must be idempotent-safe.** Use `IF NOT EXISTS` for `CREATE TABLE` in additive migrations. Use `ON CONFLICT DO NOTHING` for seed data.
2. **Never modify a versioned migration after it has been applied.** Create a new versioned migration instead.
3. **Test on both H2 and PostgreSQL.** Run `make test` (H2) and `make test-integration` (Testcontainers PostgreSQL).
4. **Avoid PostgreSQL-specific syntax** unless absolutely necessary (e.g., `jsonb`, `SERIAL`, `gen_random_uuid()`). Use ANSI SQL.
5. **Always add indexes explicitly.** Do not rely on Hibernate auto-index generation.
6. **Constraint names are mandatory.** Never use anonymous constraints.

## Review Checklist

Before merging a migration:

- [ ] SQL is valid on both PostgreSQL and H2
- [ ] All constraints have explicit names
- [ ] Indexes are defined for foreign-key-like columns
- [ ] `make test` passes (H2 unit tests)
- [ ] `make test-integration` passes (PostgreSQL Testcontainers)
- [ ] Hibernate `validate` passes at startup
- [ ] Migration is forward-only (no destructive changes without a new migration)
