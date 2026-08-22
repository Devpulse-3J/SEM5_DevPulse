# Metrics Service Implementation

This document describes the implemented design of `backend/metrics-service`, including its DORA
definitions, persistence model, event flow, security boundaries, design patterns, and operational
trade-offs. The separate [Metrics API Guide](metrics_service_api.md) documents the HTTP contract.

## Responsibilities

The service is the system of record for engineering activity. It:

- consumes `pr.opened`, `pr.merged`, `pr.closed`, `commit.pushed`, and
  `deployment.created` events from RabbitMQ;
- idempotently writes its owned `pull_requests`, `commits`, and `deployments` tables;
- calculates all four DORA metrics directly from production deployment and commit facts;
- stores daily, rolling DORA snapshots in `dora_metrics` for history;
- returns project pull requests, deployment history, and developer workload;
- enforces company isolation and project membership on every HTTP query.

`issue.updated` is intentionally not persisted by this service. `jira_issues` is owned by
integration-service and is not an input to any implemented metric.

## Design

The implementation uses the existing Spring Boot structure and keeps persistence, business rules,
transport, and API payloads separate:

| Area | Responsibility |
|---|---|
| `controller` | HTTP parameter validation and delegation only |
| `dto` | Stable JSON response and error shapes |
| `security` | Resolve gateway identity and enforce tenant/project access |
| `consumer` | Typed RabbitMQ adapter |
| `entity` / Spring Data repositories | Transactional writes to service-owned tables |
| JDBC query repositories | Purpose-built, read-only joins and aggregate inputs |
| `service` | Use-case orchestration, mapping, snapshots, and ingestion |
| `service/calculation` | One calculation Strategy per DORA metric |
| `domain` | Metric windows, deployment facts, keys, results, and ratings |

The main patterns are:

- **Strategy:** `DoraMetricCalculator` has four implementations. Each formula can be changed and
  tested without changing the orchestrator.
- **Repository:** SQL/JPA details stay outside use-case services. JPA is used for transactional
  event writes; JDBC projections are used for efficient read models and analytical joins.
- **Policy object:** `DoraRatingPolicy` is the single source of performance-band thresholds.
- **DTO/Mapper boundary:** database entities are never serialized by controllers.
- **Dependency injection:** clocks, repositories, strategies, and configuration are injected,
  making time-based calculations deterministic in tests.

### Why there are multiple files

The service has several independent jobs: four formulas, four API views, five event types,
database writes, analytical reads, tenant authorization, queue configuration, scheduling, and
error handling. Java normally gives each public class one file. Keeping these boundaries avoids a
single controller or “god service” that mixes SQL, authorization, formulas, JSON mapping, and
RabbitMQ handling.

The audit removed unused entity/repository/domain files left by the interrupted implementation.
The remaining files correspond to active responsibilities. Small records such as DTOs and domain
facts are separate because they are contracts, not extra processing layers.

## DORA calculation rules

All calculations use a UTC half-open interval `[calculatedAt - windowDays, calculatedAt)`. Only
rows where `deployments.environment = 'production'` and the request's `company_id` and
`project_id` match are eligible. `pending` deployments never count as completed deployments.

| Metric | Implemented formula | No-data behavior | Stored scale / API scale |
|---|---|---|---|
| Deployment Frequency | successful production deployments / `windowDays` | `0`, rating `LOW` | deployments/day in both |
| Lead Time for Changes | mean of `deployed_at - commit_time` for successful deployments with a linked commit | `null`, `NOT_AVAILABLE` | hours in both |
| Change Failure Rate | (`failed` + `rolled_back`) / (`success` + `failed` + `rolled_back`) | `null`, `NOT_AVAILABLE` | ratio 0–1 in DB; percentage 0–100 in API |
| MTTR | mean of `failure_recovered_at - deployed_at` for recovered failed/rolled-back deployments | `null`, `NOT_AVAILABLE` | hours in both |

Invalid negative durations are excluded. `sampleSize` in the API shows the denominator actually
used, so consumers can distinguish a stable result from a small sample.

### Rating policy

Ratings operate on database-native values (the failure rate is a ratio here):

| Metric | ELITE | HIGH | MEDIUM | LOW |
|---|---:|---:|---:|---:|
| Deployment Frequency | ≥ 1/day | ≥ 1/week | ≥ 1/month | below 1/month |
| Lead Time | ≤ 24 h | ≤ 168 h | ≤ 720 h | > 720 h |
| MTTR | ≤ 1 h | ≤ 24 h | ≤ 168 h | > 168 h |
| Change Failure Rate | ≤ 5% | ≤ 10% | ≤ 15% | > 15% |

The bands are centralized in `DoraRatingPolicy`, because benchmark definitions may be revised in
future releases. Missing samples receive `NOT_AVAILABLE`; they are never incorrectly labelled
elite.

### Current, previous, and history values

`GET /metrics/dora` calculates the current window live and calculates `previousValue` from the
immediately preceding, non-overlapping window. A UTC scheduler captures one rolling snapshot per
project every day at 00:05 by default. The `dora_metrics` unique key
`(project_id, calculated_date, window_days)` makes the snapshot upsert idempotent. The live value
replaces today's snapshot in the returned history, so the endpoint never serves a stale current
value.

## Other metrics views

### Workload

Workload includes current project members. For each member:

- `activePrs` is the number of authored PRs whose state is `open` (drafts are included because
  they are active work);
- `loadPct = activePrs / WORKLOAD_TARGET_ACTIVE_PRS * 100`; the target defaults to 4;
- `cycleTimeHours` is the mean `merged_at - created_at` for PRs merged inside the requested
  window; it is `null` when there are no merged samples.

The percentage is not capped at 100, so overload remains visible.

### Pull requests

The service reads PRs and then batch-loads reviews and checks for the returned IDs. This avoids
an N+1 query per PR. `is_draft` takes precedence over the stored state when producing the API
status. `riskAnalysis` is returned as `null`; analytics-service owns `pr_predictions` and clients
fetch risk from its endpoint separately.

### Deployments

Deployment history supports environment/status filters and calculates a per-row lead time only
when a valid linked commit exists.

## Event ingestion

`devpulse.metrics.events` is a durable queue bound to `pr.*`, `commit.pushed`, and
`deployment.created` on `devpulse.events`. A failed message is retried three times and then routed
to `devpulse.metrics.events.dlq`.

Idempotency comes from natural/external keys:

- PR: `(repo_id, github_pr_id)`, with `(repo_id, github_pr_number)` as fallback;
- commit: `commit_sha`;
- deployment: `(company_id, github_deployment_id)`.

External repository and user IDs are resolved to internal foreign keys before a write. A missing
mapping fails the event instead of inserting cross-tenant or invalid foreign keys. If a failed
deployment later receives a successful status, it remains a failed change and
`failure_recovered_at` records the recovery time. This preserves both change-failure-rate and
MTTR evidence.

## Authorization and tenant isolation

The gateway validates JWTs and currently forwards `X-User-Id` and `X-Company-Id`. The service
also understands the documented `X-DevPulse-*` names for compatibility. For every endpoint it:

1. resolves the project only inside the forwarded company;
2. verifies that the forwarded user belongs to that company;
3. permits company `admin` users, otherwise requires a `project_members` row.

The service port should not be publicly exposed in production. Identity headers are trusted only
when the service is reachable through a gateway that removes client-supplied copies.

## Schema migration

`V5__extend_metrics_service_schema.sql` is forward-only because `V1`–`V4` may already have run.
It adds:

- documented PR fields (`github_pr_id`, description, head branch, URL, update time);
- `users.avatar_url`, needed by PR/review response DTOs;
- `pr_checks` and the `pending` review state;
- `deployments.github_deployment_id` for idempotent event updates;
- `dora_metrics.calculated_at` for precise snapshot metadata;
- partial unique and DORA-window indexes used by ingestion and calculations.

The four formulas still use the original V1 commit/deployment columns. The ERD was updated with
the migration.

## Non-functional behavior

- Tenant and project predicates are present in analytical queries.
- The DORA window index covers the high-frequency production/time query.
- API list endpoints are bounded to at most 500 rows per request.
- Read use cases are transactional read-only; ingestion and snapshot upserts are transactional.
- Queue retry plus a dead-letter queue prevents poison messages from blocking the consumer.
- UTC `Instant`/`Clock` is used throughout; no server-local timezone enters a calculation.
- Errors use the repository-wide `{ "error": { "code", "message" } }` envelope.

## Known schema/contract limitations

- A deployment links only one `commit_sha`. Lead time is therefore the time for that linked/head
  commit, not the mean of every commit included in a release. A deployment-to-commits join table
  would be needed for release-level fidelity.
- `commits.commit_sha` is a global primary key in V1. The consumer refuses to overwrite a SHA
  already owned by another company or repository, but identical SHAs in forks cannot both be
  stored without a future composite-key migration.
- Shared event contracts currently represent GitHub PR/repository/deployment IDs as Java
  `Integer`; GitHub IDs can exceed that range. Those contracts and integration-service should be
  migrated together to `Long` in a separately coordinated change.
- Historical charts begin accumulating when the scheduler starts. V5 does not manufacture old
  snapshots from current data.

## Verification

Run from `backend/`:

```bash
mvn -B -pl metrics-service -am test
```

The suite covers all four formulas, missing-data semantics, ratings, identity-header handling,
project access control, deployment recovery transitions, and cross-tenant commit protection.
