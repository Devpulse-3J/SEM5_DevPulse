# DevPulse API Contract & Gateway Scaffold

The frontend is built and running on mocks. This document is the agreement that lets it switch
to real data without changing a line of frontend code.

**Read this before writing any controller.** Field names here are copied from the frontend's
TypeScript types — they are not negotiable, and a mismatched field name is a silent runtime
failure in the UI.

---

## 1. Stack (already fixed by the repo — no decision to make)

| Layer | Choice | Where it is pinned |
|---|---|---|
| Java services | Java 17 · Spring Boot 3.3.5 | `backend/pom.xml` |
| Gateway | Spring Cloud Gateway 2023.0.3 (reactive, WebFlux) | `backend/api-gateway/pom.xml` |
| Persistence | Spring Data JPA / Hibernate 6 | per-service POMs |
| Database | **One shared** PostgreSQL 16 · `devpulse` | `infrastructure/docker/docker-compose.yml` |
| Migrations | Flyway, single owner in `backend/database/migrations/` | ditto |
| ML service | Python 3.11 · FastAPI | `backend/analytics-service/` |
| Messaging | RabbitMQ 3.13 | ditto |
| Cache / rate limit | Redis 7 | ditto |

The gateway is **reactive** (`spring-cloud-starter-gateway` pulls WebFlux). Do **not** add
`spring-boot-starter-web` to `api-gateway` — mixing the two stacks breaks startup.

---

## 2. Global conventions

**Base URL.** Frontend calls `http://localhost:8080` (`NEXT_PUBLIC_API_BASE_URL`). Everything is
under `/api`. The gateway strips `/api` before forwarding, so a service controller maps
`/auth/login`, not `/api/auth/login`.

**Auth.** `Authorization: Bearer <jwt>`, HS256, signed with the shared `JWT_SECRET`. The gateway
validates the signature and expiry at the edge, then forwards identity as headers:

```
X-DevPulse-User-Id      the JWT subject
X-DevPulse-Company-Id   companyId claim
X-DevPulse-System-Role   admin | member
```

Services trust those headers **only** because the gateway strips any inbound copy first. Services
still resolve the per-project role themselves from `project_members`.

**Error shape — every service, every failure:**

```json
{ "error": { "code": "PROJECT_NOT_FOUND", "message": "No project with id 42" } }
```

`code` is SCREAMING_SNAKE and stable (the frontend may switch on it); `message` is human-readable
and may change. Status codes: `400` validation, `401` missing/invalid token, `403` authenticated
but wrong role, `404` not found, `409` conflict (duplicate email), `500` unexpected.

**IDs are strings in JSON.** The database uses `integer` identity columns; serialise them as
strings (`"id": "42"`) because the frontend types say `string`. Do this in the DTO/mapper, not by
changing the schema.

**Timestamps** are ISO-8601 UTC with offset: `2026-08-05T14:31:00Z`. Java type is `OffsetDateTime`
(the columns are `timestamptz` — `LocalDateTime` will fail Hibernate validation).

**Role casing.** The database stores lowercase (`'admin'`, `'manager'`, `'developer'`); the
frontend expects uppercase (`"ADMIN"`, `"MANAGER"`, `"DEVELOPER"`). Convert in the mapper.

---

## 3. Route map — who implements what

The gateway routes only. Every endpoint below is implemented in the owning service.

| Path prefix | Service | Port | Owner |
|---|---|---|---|
| `/api/auth/**`, `/api/users/**`, `/api/orgs/**`, `/api/projects/**` | auth-service | 8081 | Kalhara |
| `/api/metrics/**` | metrics-service | 8083 | Kalhara |
| `/api/repositories/**`, `/api/integrations/**`, `/api/webhooks/**` | integration-service | 8082 | Umaya |
| `/api/notifications/**` | notification-service | 8084 | Umaya |
| `/api/analytics/**` | analytics-service | 8000 | Didula |

---

## 4. Gateway scaffold — step by step (Didula)

### 4.1 Add JWT parsing to `backend/api-gateway/pom.xml`

```xml
<dependency>
  <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
```

### 4.2 Replace `backend/api-gateway/src/main/resources/application.yml`

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: api-gateway
  data:
    redis:
      url: ${REDIS_URL:redis://localhost:6379}
  cloud:
    gateway:
      # Strips /api so downstream controllers map /auth/login, /metrics/dora, ...
      default-filters:
        - StripPrefix=1
      routes:
        - id: auth-service
          uri: ${AUTH_SERVICE_URI:http://localhost:8081}
          predicates:
            - Path=/api/auth/**,/api/users/**,/api/orgs/**,/api/projects/**
        - id: integration-service
          uri: ${INTEGRATION_SERVICE_URI:http://localhost:8082}
          predicates:
            - Path=/api/repositories/**,/api/integrations/**,/api/webhooks/**
        - id: metrics-service
          uri: ${METRICS_SERVICE_URI:http://localhost:8083}
          predicates:
            - Path=/api/metrics/**
        - id: analytics-service
          uri: ${ANALYTICS_SERVICE_URI:http://localhost:8000}
          predicates:
            - Path=/api/analytics/**
        - id: notification-service
          uri: ${NOTIFICATION_SERVICE_URI:http://localhost:8084}
          predicates:
            - Path=/api/notifications/**
      globalcors:
        cors-configurations:
          '[/**]':
            # allow-credentials forbids "*" — origins must be listed explicitly.
            allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}
            allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600

devpulse:
  gateway:
    jwt:
      secret: ${JWT_SECRET:change-me}
    public-paths: >-
      /api/auth/register,
      /api/auth/login,
      /api/auth/refresh,
      /api/webhooks/**,
      /actuator/**

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

> **Ports matter.** Each route points at a *different* port. `8080` is the gateway itself —
> pointing a route there makes the gateway proxy to itself.

### 4.3 `filter/JwtAuthenticationFilter.java`

A `GlobalFilter` that:
1. lets `OPTIONS` through untouched (CORS preflight carries no token);
2. strips any inbound `X-DevPulse-*` header — a client must never be able to forge identity;
3. skips validation for `devpulse.gateway.public-paths`;
4. otherwise parses the bearer token, and on any `JwtException` returns `401` with the standard
   error body;
5. on success injects `X-DevPulse-User-Id`, `-Company-Id`, `-System-Role`.

Order: `HIGHEST_PRECEDENCE + 100` — after CORS, before routing.

### 4.4 `exception/GatewayErrorAttributes.java`

Gateway-generated failures (401, 404 no-route, 503 downstream down) must use the same error
envelope as the services, or the frontend's error handling breaks on infrastructure errors.
Override `DefaultErrorAttributes` to emit `{ "error": { "code": ..., "message": ... } }`.

### 4.5 Environment

Add to `backend/api-gateway/.env.local.example`:
`AUTH_SERVICE_URI`, `INTEGRATION_SERVICE_URI`, `METRICS_SERVICE_URI`,
`NOTIFICATION_SERVICE_URI`, `ANALYTICS_SERVICE_URI`, `CORS_ALLOWED_ORIGINS`.
Add the same as container-name URLs to the `api-gateway` service in
`infrastructure/docker/docker-compose.yml`.

### 4.6 Verify before handing off

```bash
curl -i localhost:8080/actuator/health                       # 200
curl -i localhost:8080/api/metrics/dora                      # 401, correct error body
curl -i -X OPTIONS localhost:8080/api/auth/login \
     -H 'Origin: http://localhost:3000' \
     -H 'Access-Control-Request-Method: POST'                # 200 + CORS headers
```

---

## 5. Endpoint contracts

Shapes are exactly as the frontend expects. "Source" names the tables; **"Gap"** flags what the
current schema cannot supply — all gaps are closed by `V4` (section 6).

### 5.1 auth-service (Kalhara)

```
POST /api/auth/login     {email, password}       → { token, user: User }
POST /api/auth/register  {email, password, name} → { token, user: User }
POST /api/auth/refresh   {refreshToken}          → { token }
GET  /api/users/me       (Bearer)                → User
```

```ts
User = { id, email, name, memberships: Membership[] }
Membership = { projectId, projectName, role: "ADMIN"|"MANAGER"|"DEVELOPER" }
```

- **Source:** `users` (`full_name` → `name`), `project_members` ⋈ `projects`.
- **ADMIN is not a per-project row.** The schema has `users.system_role IN ('admin','member')`
  and `project_members.role IN ('manager','developer')`. When `system_role = 'admin'`, synthesise
  a `role: "ADMIN"` membership for **every project in the company** so the frontend's project
  picker shows admins everything. Document this in the mapper — it is not obvious.
- `register` creates a **company + user** and makes that user its admin.
- `users.email` is globally `UNIQUE`, not per company → duplicate registration is `409`.
- **Refresh tokens:** no table exists. For the 2-week scope issue a second, longer-lived JWT and
  validate it statelessly. No revocation — accept it, note it as debt.

```
GET /api/orgs                  → Org[]
GET /api/orgs/{id}/invitations → Invitation[]
```

```ts
Org = { id, name, projects: Project[] }
Project = { id, name, repository, status: "ACTIVE"|"ARCHIVED", memberCount }
```

- **Source:** `companies` → `Org`, `projects` → `Project`.
- `repository`: first row in `repos` for that project (`full_name`).
- `memberCount`: `count(*)` from `project_members`.
- **Gap:** `projects.status` and the whole `invitations` table do not exist → V4.

### 5.2 metrics-service (Kalhara)

```
GET /api/metrics/dora?projectId=
GET /api/metrics/workload?projectId=
GET /api/metrics/deployments?projectId=
GET /api/metrics/prs?projectId=
```

`dora` and `workload` shapes are under negotiation — see section 7. Implement `prs` and
`deployments` as specified.

```ts
PullRequest = { id, number, title, description, author, authorAvatar?, repositoryId,
  repositoryName, status: "open"|"merged"|"closed"|"draft", headBranch, baseBranch,
  additions, deletions, changedFiles, url, createdAt, updatedAt, mergedAt?,
  reviews: PRReview[], checks: PRCheck[], riskAnalysis: PRRiskAnalysis }
PRReview = { id, reviewerName, reviewerAvatar?, state, submittedAt? }
PRCheck  = { id, name, status: "SUCCESS"|"FAILURE"|"IN_PROGRESS"|"QUEUED", url? }
```

- **Source:** `pull_requests` (`github_pr_number`→`number`, `lines_added`→`additions`,
  `lines_deleted`→`deletions`, `files_changed`→`changedFiles`), `pr_reviews`, `repos`, `users`.
- **`status` is two columns.** The schema has `state IN ('open','closed','merged')` *and*
  `is_draft boolean`. Map: `is_draft → "draft"`, else lowercase `state`.
- **`riskAnalysis` is owned by analytics-service.** metrics-service must **not** query
  `pr_predictions` for a computed view — call `GET /analytics/prs/{id}/risk`, or return
  `riskAnalysis: null` in the list view and let the detail page fetch it. Prefer the latter for
  two weeks; N+1 HTTP calls in a list endpoint will be slow and fragile.
- **Gap:** `description`, `head_branch`, `url`, `updated_at` on `pull_requests`; `users.avatar_url`;
  the entire `pr_checks` table; `'pending'` missing from the `pr_reviews.review_state` CHECK → V4.

### 5.3 analytics-service (Didula)

```
GET /api/analytics/prs/{id}/risk → PRRiskAnalysis
```

```ts
PRRiskAnalysis = { riskScore: number /* 0-100 */, riskLevel: "LOW"|"MEDIUM"|"HIGH"|"CRITICAL",
  summary: string, factors: { category, description, impactScore }[] }
```

- **Source:** `pr_predictions` — the only table analytics-service may write.
- **Scale mismatch:** `risk_score` is `numeric(5,4)` (0–1); the frontend wants 0–100. Multiply in
  the response DTO; do **not** change the column.
- **Gap:** `risk_category` CHECK allows only `low|medium|high` — no `critical`; no `summary`,
  no `factors` columns → V4.

### 5.4 integration-service (Umaya)

```
GET  /api/repositories?projectId=  → Repository[]
GET  /api/repositories/{id}        → Repository (+ branches[], recentCommits[])
POST /api/repositories/{id}/sync   → 202 Accepted
GET  /api/integrations             → { github, jira, slack }
POST /api/integrations/github/connect
```

```ts
Repository = { id, name, owner, fullName, description, url, defaultBranch, language,
  isPrivate, status: "active"|"archived"|"syncing"|"error", starsCount, forksCount,
  metrics: { openPullRequests, openIssues, codeCoverage?, healthScore, lastSyncAt },
  branches?, recentCommits?, createdAt, updatedAt }
```

- **Source:** `repos` supplies only `repo_name`, `owner_name`, `full_name`, `default_branch`.
- `metrics.openPullRequests`: count `pull_requests` where `state='open'` (read-only cross-service
  query — allowed; writes are not).
- `recentCommits`: `commits` ordered by `commit_time DESC LIMIT 10`.
- **Gap:** `description`, `url`, `language`, `is_private`, `status`, `stars_count`, `forks_count`,
  `open_issues`, `code_coverage`, `health_score`, `last_sync_at`, `created_at`, `updated_at`; no
  branches table; `integrations.last_sync_at` → V4.
- `POST /sync` returns `202` immediately and publishes to RabbitMQ. For two weeks it may simply
  update `last_sync_at` and return — say so in the response, don't fake progress.

### 5.5 notification-service (Umaya)

```
GET /api/notifications/rules    → AlertRule[]
GET /api/notifications/history  → SystemAlert[]
GET /api/notifications/channels → Channel[]
```

```ts
AlertRule = { id, name, metric, threshold, condition: "ABOVE"|"BELOW"|"EQUALS",
  channels: ("slack"|"email"|"in_app")[], enabled, createdAt }
SystemAlert = { id, ruleId?, title, message, severity: "INFO"|"WARNING"|"HIGH"|"CRITICAL",
  repositoryName?, targetUrl?, timestamp, isAcknowledged, acknowledgedBy?, acknowledgedAt? }
```

- **Source:** `alert_rules`, `alerts`, `notifications`.
- **Gap:** `alert_rules` has `rule_type`/`threshold_hours`/`slack_channel`/`is_active` but no
  `name`, `metric`, `condition`, or `channels[]`. `alerts` has no `title`, no acknowledgement
  columns, no `target_url`, and its severity CHECK lacks `'high'` → V4.

---

## 6. `V4__align_schema_with_api_contract.sql` (Didula writes it — day 1)

Nothing above can be built until this lands, because `ddl-auto: validate` crashes any service
whose entity does not match the table. **Didula assigns migration numbers; do not create your own
`V4`.**

| Table | Change |
|---|---|
| `users` | `+ avatar_url varchar(512)` |
| `projects` | `+ status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED'))` |
| `repos` | `+ description text`, `url varchar(512)`, `language varchar(50)`, `is_private boolean NOT NULL DEFAULT false`, `status varchar(20) NOT NULL DEFAULT 'active'`, `stars_count/forks_count/open_issues integer NOT NULL DEFAULT 0`, `code_coverage numeric(5,2)`, `health_score integer`, `last_sync_at timestamptz`, `created_at/updated_at timestamptz NOT NULL DEFAULT now()` |
| `pull_requests` | `+ description text`, `head_branch varchar(255)`, `url varchar(512)`, `updated_at timestamptz NOT NULL DEFAULT now()` |
| `pr_reviews` | drop + recreate CHECK to add `'pending'` |
| `pr_predictions` | drop + recreate CHECK to add `'critical'`; `+ summary text`, `+ factors jsonb` |
| `alert_rules` | `+ name varchar(255)`, `metric varchar(50)`, `condition varchar(20) CHECK IN ('ABOVE','BELOW','EQUALS')`, `channels jsonb NOT NULL DEFAULT '[]'` |
| `alerts` | drop + recreate severity CHECK to add `'high'`; `+ title varchar(255)`, `target_url varchar(512)`, `acknowledged_by_user_id integer REFERENCES users`, `acknowledged_at timestamptz` |
| `integrations` | `+ last_sync_at timestamptz` |
| **new** `pr_checks` | `check_id`, `pr_id → pull_requests`, `name`, `status CHECK IN ('SUCCESS','FAILURE','IN_PROGRESS','QUEUED')`, `url` |
| **new** `repo_branches` | `branch_id`, `repo_id → repos`, `branch_name`, `is_default boolean`, `last_commit_sha` |
| **new** `invitations` | `invitation_id`, `company_id → companies`, `email`, `role`, `status`, `invited_by_user_id`, `created_at`, `expires_at` |

Then `V5__seed_demo_data_v2.sql`: realistic volume — ~3 repos, ~40 PRs spread over 8 weeks with
reviews/checks, ~30 deployments, ~20 predictions, alert rules and alerts. **Every endpoint returns
real database rows from day one; only the ingestion is deferred.** Existing `V3` stays untouched.

---

## 7. The two UI-oriented shapes — proposal

`/metrics/dora` and `/metrics/workload` currently carry presentation fields the frontend used to
compute for itself: `sparkline`, `severity`, `ratingLabel`, `updatedAgo`, `loadSeverity`,
`initials`. Cleanly, those belong in the UI:

```ts
// Proposed — backend returns facts, frontend derives presentation
DoraSummary = {
  projectId: string, projectName: string, repoCount: number,
  calculatedAt: string,                    // ISO-8601, replaces updatedAgo
  windowDays: number,
  metrics: {
    key: "deploymentFrequency"|"leadTime"|"mttr"|"changeFailureRate",
    value: number, unit: string,
    rating: "ELITE"|"HIGH"|"MEDIUM"|"LOW", // real DORA classification, not styling
    previousValue: number,                 // replaces trend.direction/text
    history: { date: string, value: number }[]   // replaces sparkline
  }[]
}

WorkloadEntry = {
  userId: string, name: string,            // initials derived in UI
  activePrs: number, loadPct: number,      // loadSeverity derived from loadPct in UI
  cycleTimeHours: number                   // hours, not days — days loses precision
}
```

`rating` stays server-side: ELITE/HIGH/MEDIUM/LOW are the actual DORA performance bands, a domain
fact. `severity` (good/warn/bad) is pure styling and should go.

**My recommendation for the 2-week window: do not adopt this yet.** The frontend already works
against the original shapes; re-cutting them means frontend rework you have not budgeted, and
computing `initials` or a `sparkline` array server-side is a handful of lines. Ship the shapes as
specified, and land this cleanup after submission if the schedule allows. If you disagree, decide
**now** — Kalhara cannot start `/metrics/dora` until this is settled.

---

## 8. Explicitly out of scope for two weeks

State these in the report rather than half-building them:

- Live GitHub/Jira webhook ingestion end-to-end (the receiver + `raw_event_log` write is in scope;
  the full normalise → RabbitMQ → persist chain is not).
- Real ML training on real history. analytics-service serves rows from `pr_predictions`, seeded
  and/or generated by one offline training run on the seeded PRs.
- Slack/email delivery beyond a single working Slack webhook.
- Token revocation, refresh-token rotation, rate limiting, Swagger, Prometheus/Grafana.
