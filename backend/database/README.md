# DevPulse Database

This folder is the **single source of truth for the DevPulse database schema**. All backend
services share **one** PostgreSQL database, and its schema is owned and migrated **only from
here** — never from inside a service.

```
database/
├── migrations/          # Flyway migrations — the ONLY place schema changes live
│   ├── V1__create_tables.sql
│   ├── V2__seed_roles.sql
│   └── V3__seed_demo_data.sql
├── diagrams/            # erd.dbml (source) → erd.png (export from dbdiagram.io)
├── seeds/               # optional ad-hoc seed data, not run automatically
└── README.md
```

---

## Why one shared PostgreSQL database

DevPulse uses a **microservice** backend but a **single shared database**. This is a deliberate
choice for a 3-person Software Engineering semester project:

- **Simpler deployment** — one database to provision, back up, and monitor.
- **Simpler maintenance** — one schema, one connection string, one migration history.
- **Strong foreign keys already exist** — the schema relates data across service boundaries
  (`pull_requests → users`, `dora_metrics → projects`, `pr_predictions → pull_requests`). These
  integrity constraints are only possible inside one database.
- **Easier reporting and analytics** — DORA and ML queries join across domains without
  cross-service calls.
- **Focus on software engineering, not distributed data** — the learning goal is services,
  REST, and messaging, not managing distributed databases and eventual consistency.

**Database-per-service is intentionally NOT used.** The PostgreSQL database is **not** a
microservice — it is an infrastructure component, deployed separately (Docker locally; Neon,
Supabase, or AWS RDS in the cloud). Every service connects to the **same** database.

---

## Logical table ownership

Physically the tables live in one database. Logically each table is **owned by exactly one
service**. The rule:

> **A service may READ any table it needs. A service may WRITE only the tables it owns.**

| Service | Owns (writes) |
|---|---|
| **auth-service** | `companies`, `users`, `projects`, `project_members`, `integrations` |
| **integration-service** | `repos`, `jira_issues`, `raw_event_log` |
| **metrics-service** | `pull_requests`, `pr_reviews`, `commits`, `deployments`, `dora_metrics` |
| **analytics-service** | `pr_predictions` |
| **notification-service** | `alert_rules`, `alerts`, `notifications` |

`api-gateway` owns no tables (it holds no database connection). This ownership is a **convention
enforced in code review**, not by database permissions — respect it everywhere. If a service
needs to *change* data it does not own, it calls the owning service over REST or reacts to a
RabbitMQ event; it never writes another service's tables directly.

---

## One migration owner

There is exactly **one** Flyway migration owner: this `database/` folder. Individual services
**must not** contain `db/migration/` folders, **must not** bundle Flyway, and **must** set:

```properties
spring.flyway.enabled=false
```

(The Spring services already carry `SPRING_FLYWAY_ENABLED=false` in their `.env.local.example`.)

Only the dedicated migration process — the `flyway` container in
`infrastructure/docker/docker-compose.yml`, pointed at `database/migrations/` — runs migrations.
This prevents five services from racing to migrate the same shared database.

---

## Migration naming convention

Flyway versioned migrations, applied in order, each run exactly once:

```
V<version>__<snake_case_description>.sql
```

- `V` is capital; the separator is a **double** underscore `__`.
- Versions are integers that only ever increase: `V1`, `V2`, `V3`, …
- Every schema change is a **new** migration. Once a migration has run in any shared
  environment, treat it as immutable — **never edit or renumber it** (Flyway stores a checksum
  and will refuse a changed file). To undo something, write a new higher-numbered migration.

Current migrations:

| File | Purpose |
|---|---|
| `V1__create_tables.sql` | All 17 tables, constraints, and indexes |
| `V2__seed_roles.sql` | Documented no-op — roles are CHECK constraints, not a table |
| `V3__seed_demo_data.sql` | Local-only demo company/users/project (do not use in prod) |

---

## How migrations are executed

Migrations run against the shared database via the `flyway` service in Docker Compose. It waits
for Postgres to be healthy, applies everything in `database/migrations/`, then exits. The backend
services `depend_on` it completing, so they only start against a migrated schema.

```bash
# From the repo root. Postgres + Flyway come up; Flyway applies all pending migrations.
docker compose -f infrastructure/docker/docker-compose.yml up -d postgres flyway

# Check what has been applied
docker compose -f infrastructure/docker/docker-compose.yml logs flyway
```

To run migrations against a cloud database instead (Neon/Supabase/RDS), point the Flyway
`FLYWAY_URL`/`FLYWAY_USER`/`FLYWAY_PASSWORD` at it, or run the Flyway CLI locally against
`database/migrations/`.

---

## Development workflow — adding a schema change

1. Create the next file: `database/migrations/V<n>__short_description.sql`.
2. Write forward-only SQL (create/alter table, add index, add a column with a default, …).
3. If you change the shape of the data, update `diagrams/erd.dbml` in the same PR and re-export
   `erd.png` from [dbdiagram.io](https://dbdiagram.io).
4. If the change affects an API payload or event shape, update `backend/shared-contracts/` and
   `frontend/shared-types/` too — they must stay in sync with the schema.
5. Re-run the `flyway` service (above). On a clean local database you can instead reset:
   ```bash
   docker compose -f infrastructure/docker/docker-compose.yml down -v   # drops the volume
   docker compose -f infrastructure/docker/docker-compose.yml up -d postgres flyway
   ```
6. Note in code review which service **owns** any new table, and keep writes to it inside that
   service.
