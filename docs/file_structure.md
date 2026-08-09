# DevPulse — File Structure & Ownership

Every file in this repo, with the member who **owns** it. "Owns" means: you write it, you review
PRs that touch it, and you are the person to ask about it (see [separation.md](separation.md)).

Ownership is derived from [CLAUDE.md](../CLAUDE.md) and [separation.md](separation.md):

| Tag | Member | Scope |
|---|---|---|
| `[D]` | **Didula** | `api-gateway`, `analytics-service`, `infrastructure/`, `backend/database/` (migration mechanism), `.github/workflows/`, `shared-contracts/openapi/`, Phase-0 build files |
| `[K]` | **Kalhara** | `auth-service`, `metrics-service`, `shared-contracts/dto/`, JWT/RBAC design, DORA computation |
| `[U]` | **Umaya** | `integration-service`, `notification-service`, `shared-contracts/events/` |
| `[*]` | **Shared** | Team-wide docs / repo config — any member may edit, all three review |

> **Phase-0 note:** Didula bootstrapped every service skeleton (POMs, entrypoints, Dockerfiles,
> `application.yml`). Those files are now maintained by the **service owner**, not by Didula —
> the table above reflects the ongoing owner. Only the *parent* `backend/pom.xml` and
> `backend/.dockerignore` stay with Didula, since a change there affects all five modules.

---

## Repository root

```
[*] .gitignore
[*] README.md                                  # long-form project doc (predates some decisions)
[*] CLAUDE.md                                  # gitignored — local Claude Code guidance
[D] .github/workflows/.gitkeep                 # CI skeleton; per-service pipeline filled in by each owner
[*] scripts/.gitkeep
```

## docs/ — orientation documents (shared)

```
[*] docs/.gitkeep
[*] docs/separation.md                         # who owns what
[*] docs/service.md                            # what each service is for
[*] docs/api.md                                # untracked
[*] docs/plan-2weeks.md                        # untracked
[*] docs/file_structure.md                     # this file
```

## backend/ — build root

```
[D] backend/pom.xml                            # parent POM, aggregates all 6 modules
[D] backend/.dockerignore
```

## backend/api-gateway/ — Didula · port 8080 · no DB

```
[D] backend/api-gateway/.env.local.example
[D] backend/api-gateway/Dockerfile
[D] backend/api-gateway/pom.xml
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/ApiGatewayApplication.java
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/config/.gitkeep
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/controller/.gitkeep
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/dto/.gitkeep
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/exception/.gitkeep
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/filter/.gitkeep
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/route/.gitkeep
[D] backend/api-gateway/src/main/java/com/devpulse/gateway/security/.gitkeep
[D] backend/api-gateway/src/main/resources/application.yml
[D] backend/api-gateway/src/test/java/com/devpulse/gateway/.gitkeep
```

No `entity/` `repository/` `mapper/` — the gateway holds no DB connection.
Edge JWT validation here depends on Kalhara's `auth-service`.

## backend/analytics-service/ — Didula · port 8000 · Python/FastAPI · the ML layer

```
[D] backend/analytics-service/.env.local.example
[D] backend/analytics-service/Dockerfile
[D] backend/analytics-service/requirements.txt
[D] backend/analytics-service/app/__init__.py
[D] backend/analytics-service/app/main.py                      # entrypoint; currently only /health
[D] backend/analytics-service/app/api/__init__.py               # FastAPI routers
[D] backend/analytics-service/app/services/__init__.py          # inference / business logic
[D] backend/analytics-service/app/services/.gitkeep
[D] backend/analytics-service/app/ml/__init__.py                # features, training, model code
[D] backend/analytics-service/app/consumers/__init__.py         # RabbitMQ pr.* listeners
[D] backend/analytics-service/app/schemas/__init__.py           # Pydantic models
[D] backend/analytics-service/app/schemas/.gitkeep
[D] backend/analytics-service/app/database/__init__.py          # SQLAlchemy session + pr_predictions
[D] backend/analytics-service/app/utils/__init__.py
[D] backend/analytics-service/app/artifacts/.gitkeep            # serialised models (*.joblib gitignored)
[D] backend/analytics-service/tests/__init__.py
[D] backend/analytics-service/tests/.gitkeep
```

Writes only `pr_predictions`. **Never add Alembic or any migration tool here.**

## backend/auth-service/ — Kalhara · port 8081

```
[K] backend/auth-service/.env.local.example
[K] backend/auth-service/Dockerfile
[K] backend/auth-service/pom.xml
[K] backend/auth-service/src/main/java/com/devpulse/auth/AuthServiceApplication.java
[K] backend/auth-service/src/main/java/com/devpulse/auth/config/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/controller/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/dto/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/entity/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/exception/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/mapper/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/repository/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/security/.gitkeep      # JWT + RBAC
[K] backend/auth-service/src/main/java/com/devpulse/auth/service/.gitkeep
[K] backend/auth-service/src/main/java/com/devpulse/auth/util/.gitkeep
[K] backend/auth-service/src/main/resources/application.yml
[K] backend/auth-service/src/test/java/com/devpulse/auth/.gitkeep
```

Writes `companies`, `users`, `projects`, `project_members`, `integrations`.

## backend/metrics-service/ — Kalhara · port 8083

```
[K] backend/metrics-service/.env.local.example
[K] backend/metrics-service/Dockerfile
[K] backend/metrics-service/pom.xml
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/MetricsServiceApplication.java
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/config/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/controller/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/dto/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/entity/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/exception/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/mapper/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/rabbitmq/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/repository/.gitkeep
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/service/.gitkeep  # DORA computation
[K] backend/metrics-service/src/main/java/com/devpulse/metrics/util/.gitkeep
[K] backend/metrics-service/src/main/resources/application.yml
[K] backend/metrics-service/src/test/java/com/devpulse/metrics/.gitkeep
```

Writes `pull_requests`, `pr_reviews`, `commits`, `deployments`, `dora_metrics`.

## backend/integration-service/ — Umaya · port 8082

```
[U] backend/integration-service/.env.local.example
[U] backend/integration-service/Dockerfile
[U] backend/integration-service/pom.xml
[U] backend/integration-service/src/main/java/com/devpulse/integration/IntegrationServiceApplication.java
[U] backend/integration-service/src/main/java/com/devpulse/integration/config/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/controller/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/dto/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/entity/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/exception/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/github/.gitkeep   # webhook + signature verify
[U] backend/integration-service/src/main/java/com/devpulse/integration/jira/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/mapper/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/rabbitmq/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/repository/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/service/.gitkeep
[U] backend/integration-service/src/main/java/com/devpulse/integration/util/.gitkeep
[U] backend/integration-service/src/main/resources/application.yml
[U] backend/integration-service/src/test/java/com/devpulse/integration/.gitkeep
```

Writes `repos`, `jira_issues`, `raw_event_log`. Upstream of both metrics and analytics.

## backend/notification-service/ — Umaya · port 8084

```
[U] backend/notification-service/.env.local.example
[U] backend/notification-service/Dockerfile
[U] backend/notification-service/pom.xml
[U] backend/notification-service/src/main/java/com/devpulse/notification/NotificationServiceApplication.java
[U] backend/notification-service/src/main/java/com/devpulse/notification/config/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/controller/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/dto/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/email/.gitkeep    # SMTP delivery
[U] backend/notification-service/src/main/java/com/devpulse/notification/entity/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/exception/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/mapper/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/rabbitmq/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/repository/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/service/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/slack/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/util/.gitkeep
[U] backend/notification-service/src/main/java/com/devpulse/notification/webhook/.gitkeep
[U] backend/notification-service/src/main/resources/application.yml
[U] backend/notification-service/src/test/java/com/devpulse/notification/.gitkeep
```

Writes `alert_rules`, `alerts`, `notifications`.

## backend/shared-contracts/ — split three ways

This is the one module with **mixed ownership** — each sub-folder has a different owner.

```
[*] backend/shared-contracts/pom.xml
[*] backend/shared-contracts/src/main/java/com/devpulse/contracts/package-info.java
[U] backend/shared-contracts/events/.gitkeep       # RabbitMQ event schemas — Umaya, land early
[K] backend/shared-contracts/dto/.gitkeep          # auth + metrics DTOs — Kalhara
[D] backend/shared-contracts/openapi/.gitkeep      # API surface through the gateway — Didula
```

**Contracts move together:** changing an event/DTO here means updating
`frontend/shared-types/` (separate repo) in the same PR.

## backend/database/ — Didula owns the mechanism, everyone contributes migrations

```
[D] backend/database/README.md
[D] backend/database/migrations/V1__create_tables.sql       # all 17 tables + indexes
[D] backend/database/migrations/V2__seed_roles.sql          # deliberate no-op
[D] backend/database/migrations/V3__seed_demo_data.sql      # local-only demo data
[D] backend/database/diagrams/erd.dbml
[D] backend/database/seeds/.gitkeep
```

**Rule:** Kalhara and Umaya write migrations for *their own* tables, but submit them as PRs into
this folder. **Didula assigns the `V<n>` number** so concurrent PRs don't collide. Next is `V4`.
Never edit or renumber a migration that has already run. No service carries its own
`db/migration/` folder.

## infrastructure/ — Didula

```
[D] infrastructure/docker/docker-compose.yml
[D] infrastructure/docker/.env.example
[D] infrastructure/docker/.gitkeep
[D] infrastructure/docker/postgres/.gitkeep
[D] infrastructure/docker/rabbitmq/.gitkeep
[D] infrastructure/docker/redis/.gitkeep
[D] infrastructure/kubernetes/.gitkeep
[D] infrastructure/monitoring/.gitkeep
```

---

## Summary by owner

| Owner | Top-level areas | Tables they may write |
|---|---|---|
| **Didula** `[D]` | `backend/api-gateway/`, `backend/analytics-service/`, `backend/database/`, `infrastructure/`, `.github/workflows/`, `backend/pom.xml`, `shared-contracts/openapi/` | `pr_predictions` |
| **Kalhara** `[K]` | `backend/auth-service/`, `backend/metrics-service/`, `shared-contracts/dto/` | `companies`, `users`, `projects`, `project_members`, `integrations`, `pull_requests`, `pr_reviews`, `commits`, `deployments`, `dora_metrics` |
| **Umaya** `[U]` | `backend/integration-service/`, `backend/notification-service/`, `shared-contracts/events/` | `repos`, `jira_issues`, `raw_event_log`, `alert_rules`, `alerts`, `notifications` |
| **Shared** `[*]` | `docs/`, `README.md`, `.gitignore`, `scripts/`, `shared-contracts/pom.xml` | — |

## Rules that cut across ownership

- **Read anything, write only yours.** To change data you don't own, call the owning service over
  REST or emit an event — never write another member's tables.
- **Migrations go through Didula** for version assignment.
- **PRs:** branch `feature/<desc>` off `main`, Conventional Commits scoped to your service
  (`feat(auth): ...`), reviewed by at least one other member — and by the file's owner if it
  isn't yours.
- **No `frontend/` in this repo.** The Next.js apps live in a separate repo; only the
  `shared-contracts/` ↔ `shared-types/` sync rule crosses the boundary.
