# DevPulse — Work Separation

Three members. Each owns **two services** end to end (code, tests, Dockerfile, docs) plus a slice
of the cross-cutting platform work. "Own" means: you write it, you review PRs that touch it, and
you are the person to ask about it.

## Team & service ownership

| Member | Services owned | Focus |
|---|---|---|
| **Didula** | `api-gateway` · `analytics-service` (ML) | Platform entry + machine learning |
| **Kalhara** | `auth-service` · `metrics-service` | Identity/RBAC + DORA metrics |
| **Umaya** | `integration-service` · `notification-service` | Ingestion + alert delivery |

## Table ownership (writes)

One shared database; each member writes only the tables their services own (read anything).

| Member | Tables they write |
|---|---|
| **Kalhara** | `companies`, `users`, `projects`, `project_members`, `integrations` · `pull_requests`, `pr_reviews`, `commits`, `deployments`, `dora_metrics` |
| **Umaya** | `repos`, `jira_issues`, `raw_event_log` · `alert_rules`, `alerts`, `notifications` |
| **Didula** | `pr_predictions` (api-gateway holds no DB connection) |

## Cross-cutting work

Beyond their services, each member owns platform pieces. Split so the auth+metrics pairing (the
heaviest) carries less shared work.

### Didula — platform & setup lead
- **Phase 0 bootstrap** (parent `pom.xml`, per-service POMs, Maven-layout move, `@SpringBootApplication`
  entrypoints, per-service Dockerfiles). **Blocks everyone — do this first.**
- `infrastructure/docker/` — compose, the Postgres host-port fix, Redis, RabbitMQ.
- `backend/database/` **migration mechanism** — owns the Flyway container, migration version
  sequencing, and review of everyone's migration PRs (prevents `V<n>` collisions).
- `.github/workflows/` — CI skeleton, then a per-service pipeline each member fills in.
- `shared-contracts/openapi/` — API-surface aggregation through the gateway.

### Kalhara — identity & metrics
- **JWT / RBAC design** — resolve the open token question (`(user,project)→role`: per-request
  membership lookup vs. embedded map) before building `auth-service`.
- **DORA computation** — deployment frequency, lead time, MTTR, change failure rate.
- Writes migrations for **his** tables (submitted to Didula's migration folder via PR).
- `shared-contracts/dto/` — DTOs for auth and metrics payloads.

### Umaya — ingestion & delivery
- **RabbitMQ event bus** + `shared-contracts/events/` — defines the event schemas
  (`pr.opened`, `pr.merged`, `commit.pushed`, `deployment.created`, `issue.updated`, `alert.pr_high_risk`).
  Umaya publishes most events, so these schemas are Umaya's to own — **land them early**.
- **GitHub / Jira webhook ingestion** + external API clients + signature verification.
- **Delivery channels** — Slack, email (SMTP), custom webhooks.
- Writes migrations for **his** tables (via PR to Didula's migration folder).

## Dependencies — who blocks whom

```
Didula: Phase 0 ───────────────► blocks ALL backend coding

Umaya: event schemas (shared-contracts/events)
        │ consumed by
        ├──► Kalhara: metrics-service
        └──► Didula:  analytics-service

Kalhara: auth-service (JWT issue/validate)
        │ needed by
        └──► Didula: api-gateway (edge JWT validation)

Umaya: integration-service (publishes pr.*, commit.*, deployment.*)
        │ upstream of
        ├──► Kalhara: metrics-service (consumes them)
        └──► Didula:  analytics-service (consumes pr.*)
```

**Reading:** `integration-service` (Umaya) is upstream of both Didula's and Kalhara's metric/ML
work, and `auth-service` (Kalhara) is upstream of Didula's gateway. So the two things that unblock
the most people are **Phase 0** (Didula) and **event schemas** (Umaya).

## Suggested first-week sequence

1. **Didula** lands Phase 0 → merge to `main`. Nobody writes service code until this is in.
2. In parallel: **Umaya** lands event schemas in `shared-contracts/events/`; **Kalhara** decides
   the JWT model and lands auth DTOs.
3. **Kalhara** builds `auth-service` (login/JWT) → **Didula** wires gateway JWT validation against it.
4. **Umaya** builds `integration-service` webhook ingestion → publishes real events.
5. **Kalhara** (metrics) and **Didula** (analytics) consume those events in parallel.
6. **Umaya** builds `notification-service` once `alert.pr_high_risk` flows.

## Coordination rules

- **Contracts move together.** Change an event/DTO in `shared-contracts/` → update
  `frontend/shared-types/` in the same PR.
- **Migrations go through Didula.** Add a `V<n>__*.sql` to `backend/database/migrations/` via PR;
  Didula assigns the next version number to avoid collisions. Never edit a migration that already ran.
- **Write only your own tables.** Need to change data you don't own? Call the owning service over
  REST or emit an event — never write another member's tables directly.
- **Branch per feature** off `main` (`feature/<desc>`), Conventional Commits scoped to your service
  (`feat(auth): ...`), PR reviewed by at least one other member.
