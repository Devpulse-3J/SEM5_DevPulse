# code_idea.md — Didula's services, file by file

Prep notes for a code review. Covers the three things I own: **api-gateway**,
**metrics-service**, **analytics-service**.

For each file: *why it exists*, and *what a reviewer is likely to poke at*.
Read the "Known soft spots" section at the end last — those are the questions
I should have an answer ready for rather than be surprised by.

---

## 0. The 60-second pitch for each service

**api-gateway (8080)** — the only port the outside world can reach. It does three
jobs and deliberately nothing else: route by path prefix, verify the JWT once at
the edge and turn it into trusted identity headers, and rate-limit. It has **no
database**. Every service behind it re-checks auth, so the gateway is a
convenience layer, not the only line of defence.

**metrics-service (8083)** — two halves that never talk to each other directly.
The **write half** consumes RabbitMQ events (`pr.*`, `commit.pushed`,
`deployment.created`) and persists PRs / commits / deployments idempotently. The
**read half** serves `/metrics/*` REST endpoints that compute the four DORA
metrics on the fly, plus a nightly scheduler that snapshots them into
`dora_metrics` so the UI can draw a trend line.

**analytics-service (8000)** — the ML component. Right now it is honestly split:
`app/` is a FastAPI shell with only `/health`, and `training/` holds the real,
finished ML work — a GitHub PR collector, a point-in-time dataset builder, two
trainers, and a scorer. The interesting engineering there is **label-leakage
control**, not the model.

---

# 1. api-gateway

## Build & config

### `pom.xml`
Four dependencies, each one earning its place:
- `spring-cloud-starter-gateway` — the reactive gateway itself.
- `spring-boot-starter-data-redis-reactive` — **this is what makes rate limiting
  exist at all.** Putting reactive Redis on the classpath activates Spring's
  `GatewayRedisAutoConfiguration`, which builds the `RedisRateLimiter` bean.
  Remove this line and the `RequestRateLimiter` filter has nothing to bind to.
- `jjwt` (api / impl / jackson) — JWT verification. `impl` and `jackson` are
  `runtime` scope on purpose: only the `api` artifact should be compiled against.
- `actuator` — `/actuator/health`, `/actuator/info`.

### `Dockerfile`
Multi-stage. Build context is `backend/` (the Maven parent), not `api-gateway/`,
because this is a multi-module project — `mvn -pl api-gateway -am` needs the
parent POM to resolve. Ships a JRE image, not a JDK.

### `src/main/resources/application.yml`
The whole runtime configuration. Worth walking through in the review:
- `spring.data.redis.url` — where the rate-limiter counters live. Counters must
  be **shared**: with in-memory state, two gateway replicas each see half the
  traffic and the effective limit silently doubles.
- `spring.cloud.gateway.routes` — path-prefix routing, `StripPrefix=1` so
  `/api/metrics/**` reaches metrics-service as `/metrics/**`.
- **Route order matters.** The comment above the `projects` route explains it:
  routes evaluate in declaration order, first match wins. The GitHub linking
  endpoints are mapped under `/api/integrations/projects/{id}/github/**` rather
  than `/api/projects/*/github/**` precisely so they aren't swallowed by the
  `projects` route.
- `globalcors` — locked to `localhost:3000` and one LAN IP. The frontend is a
  single Next.js app; there is no second admin app.
- `devpulse.gateway.jwt.secret: ${JWT_SECRET}` — no default, on purpose. The
  service should refuse to start rather than boot with a guessable secret.

### `.env.local.example`
The committed template — three variables, all placeholders. The real `.env.local`
/ `.env` are gitignored (verified: `git check-ignore` matches them). Nothing with
a real secret is tracked in this repo.

## Source

### `ApiGatewayApplication.java`
Boot entrypoint, nothing else. No `@EnableX` annotations — everything the gateway
does comes from auto-configuration plus the three classes below.

### `security/JwtService.java`
The only place that knows how to verify a token.
- **Constructor validates the secret length (≥32 chars).** HS256 with a short key
  is weak, and jjwt would throw a confusing error later; failing at startup is
  better than failing per-request.
- `parseAndValidate` verifies **signature and expiry** in one call.
- `extractUserId` reads the standard **`sub`** claim. That is the DevPulse JWT
  contract — there is no `userId` claim.
- The secret is **symmetric**. It must be byte-identical to auth-service's or
  every single token fails signature validation and looks like an ordinary 401.
  That is the #1 "why is everything 401" cause on this project.

### `filter/JwtAuthenticationFilter.java`
The security-relevant file. Reviewer will spend the most time here.

Why it's a `GlobalFilter` and not a servlet filter: Spring Cloud Gateway is
**reactive**. `OncePerRequestFilter` / `HttpServletRequest` are not on the
classpath and never will be.

Order `-100` — runs before routing, so no request is forwarded before it is
authenticated.

The four things it does, in order:
1. **Strips client-supplied `X-User-Id` / `X-Company-Id` from every request,
   including public paths.** This is the single most important line in the
   service. Downstream services trust those headers completely, so a forged
   header would be a full authentication bypass. Stripping happens *before* any
   branch, so a public path cannot be used to smuggle one through.
2. Lets `OPTIONS` through — CORS preflight carries no credentials, so rejecting
   it in the JWT layer breaks the browser before CORS is even evaluated.
3. Lets public prefixes through (`/api/auth/register|login|refresh`,
   `/api/webhooks/` — webhooks authenticate with HMAC, not JWT).
4. Otherwise: require `Authorization: Bearer …`, validate, and **re-add** the
   identity headers from the validated claims.

Note `unauthorized()` returns `Mono.error(new ResponseStatusException(...))`
rather than `setStatusCode() + setComplete()`. The latter returns an **empty
body** and bypasses the error handler entirely — every gateway error goes through
one JSON shape because of this choice.

### `exception/GlobalExceptionHandler.java`
Renders every gateway error as `{status, error, path}`. Three non-obvious things:
- `@Order(-2)` — must beat Boot's own handler at `-1`, or it never runs.
- Constructor injects `WebProperties` and calls `.getResources()`.
  `WebProperties.Resources` is **not an injectable bean**; injecting it directly
  fails context startup.
- `ErrorAttributeOptions.of(...)` **retains only what is listed**. In Boot 3.3.5
  `STATUS` and `ERROR` are themselves Includes — omit them and the `status` /
  `error` keys silently vanish from the response.
- The failure *reason* is logged, never sent to the caller. A 401 says "Invalid
  or expired token" internally and just `Unauthorized` on the wire, so the
  response can't be used to probe which part of the token was wrong.

### `config/RateLimitConfig.java`
One bean: `userKeyResolver`, which decides what counts as "one caller".
- Authenticated → `user:<id>`, so one account can't exhaust another's budget.
- Anonymous → `ip:<addr>`, which is what gives `/api/auth/login` its brute-force
  resistance.
- It reads `X-User-Id`, which is safe **only because** the JWT filter at order
  `-100` already stripped and re-added it — route filters run after global
  filters. Worth saying out loud in the review; it looks like trusting a client
  header if you read this file alone.
- Never returns an empty `Mono`: `RequestRateLimiter` rejects an empty key.

**What actually happens in Redis** (good detail to have): the limiter runs a Lua
token-bucket script shipped inside the gateway jar via `EVALSHA`, against keys
`request_rate_limiter.{user:42}.tokens` and `.timestamp`. The braces are a hash
tag so both keys land on the same cluster slot. Both get `SETEX` with
TTL = `floor(capacity/rate × 2)` = 4s, so idle callers' keys expire on their own.
Redis is needed for the **atomicity** of refill-and-decrement, not just storage.

## Tests

### `src/test/java/.../ApiGatewayApplicationTests.java`
Context-loads smoke test. Cheapest test that catches the most: missing bean, bad
component scan, property with no default, circular dependency. Needs no
infrastructure — Redis connections are lazy.

### `src/test/java/.../filter/JwtAuthenticationFilterTest.java`
Six cases, all behavioural rather than implementation-coupled:
no token → 401 · malformed token → 401 · wrong scheme → 401 · the 401 body
matches the JSON error contract · a valid token is admitted · an expired token
→ 401.

### `src/test/resources/application-test.yml`
Test-scope only, never packaged. Supplies the two properties that have no default
(JWT secret, Redis URL) so CI can start the context with zero infrastructure. The
secret is deliberately >32 chars because `JwtService` rejects shorter ones.

### `src/test/test-gateway.sh`
Manual `curl` script for verifying 401 behaviour against a running stack. Not run
by Maven — it's a dev aid, which is why it lives outside the Java source root.

---

# 2. metrics-service

Read this in four blocks: **ingestion (write path)**, **query (read path)**,
**DORA calculation**, **access control**.

## Build & config

### `pom.xml`
Depends on `shared-contracts` for the event classes — the same DTOs
integration-service publishes, so a schema change breaks the build rather than
production. `web` + `data-jpa` + `validation` + `amqp` + `actuator` + the
Postgres driver at `runtime` scope.

### `application.yml`
- `ddl-auto: validate` — **Flyway owns the schema, Hibernate only checks it.**
  Combined with `flyway.enabled: false`, this service can never migrate the
  shared database. That rule matters because five services share one DB.
- `open-in-view: false` — no lazy loading leaking into the HTTP layer.
- `hibernate.jdbc.time_zone: UTC` — everything is `Instant`; no local time
  anywhere.
- `default-requeue-rejected: false` + `retry` (3 attempts, 500ms) — a bad message
  is retried three times, then goes to the DLQ instead of looping forever.
- `devpulse.metrics.*` — snapshot cron/window and the workload target, all
  env-overridable.

### `Dockerfile`, `.env.local.example`
Same multi-stage pattern as the gateway. The env template carries the explicit
comment that this service must not run migrations.

### `MetricsServiceApplication.java`
`@SpringBootApplication` + **`@EnableScheduling`** — without that second
annotation the nightly DORA snapshot job silently never fires.

## Config beans

### `config/RabbitMQConfig.java`
Declares the topology this service needs, so it is self-provisioning — bring up a
clean RabbitMQ and it wires itself.
- One durable topic exchange `devpulse.events`.
- One durable queue `devpulse.metrics.events`, with a **dead-letter route** to
  `<queue>.dlq` via the default exchange.
- Three bindings: `pr.*`, `commit.pushed`, `deployment.created`. This is the
  event-driven claim made concrete — metrics-service subscribes to what it cares
  about and integration-service has no idea it exists.
- `Jackson2JsonMessageConverter` so messages arrive as typed event objects.

### `config/TimeConfig.java`
Exposes `Clock.systemUTC()` as a bean. Small file, real reason: **every
time-dependent class injects `Clock` instead of calling `Instant.now()`**, which
is what makes the DORA calculators and the ingestion service unit-testable with a
fixed clock. This is the file that makes the tests possible.

## Domain (the vocabulary)

Pure Java, no Spring, no JPA — deliberately framework-free so it can be tested in
isolation.

| File | Why |
|---|---|
| `DeploymentFact` | Read-only record every DORA calculator consumes. Decouples the calculators from JPA entities and from SQL. |
| `DeploymentStatus` | Enum + `fromDatabase()` / `toDatabase()`. The DB stores lowercase (`CHECK` constraint); Java uses uppercase. One place owns that translation. |
| `DoraMetricKey` | The four metrics, each carrying its API name and unit — so the wire format can't drift from the enum. |
| `DoraRating` | ELITE / HIGH / MEDIUM / LOW / **NOT_AVAILABLE**. That last value is the point: "no data" is a distinct answer, not a bad score. |
| `MetricResult` | Value + `sampleSize`. Sample size ships to the UI so a metric computed from 2 deployments can be shown as weak evidence. |
| `MetricWindow` | Half-open interval `[start, end)` plus a compact-constructor guard. Half-open matters: back-to-back windows can't double-count a deployment on the boundary. |

## Entities (the write path's tables)

`PullRequestEntity`, `CommitEntity`, `DeploymentEntity` — JPA mappings for the
three tables this service **writes**. Notes:
- Field names deliberately mirror the schema; `@Column(name=…)` everywhere
  because the DB uses snake_case.
- Mostly setters, few getters — these are written by ingestion and read by SQL,
  not by JPA queries.
- `PullRequestEntity` has `@PrePersist` / `@PreUpdate` to maintain
  `created_at` / `updated_at` without the caller thinking about it.
- `CommitEntity`'s `@Id` is `commit_sha` — the natural key. **Its PK is global,
  not per-company**, which the ingestion service has to defend against (below).

## Repositories

Two styles on purpose, and a reviewer will ask why:

**Spring Data JPA** (`PullRequestRepository`, `CommitRepository`,
`DeploymentRepository`) — used by the **write** path, where I want entity
identity, dirty checking and `save()` semantics. Each exposes exactly the
idempotency lookups ingestion needs (find-by-external-id).

**Hand-written JDBC** (`ActivityQueryRepository`, `DoraQueryRepository`,
`DoraSnapshotStore`, `EventReferenceRepository`, `ProjectScopeRepository`) — used
by the **read** path. Reasons:
- These queries join across tables this service does **not** own (`repos`,
  `users`, `projects`, `project_members`). Mapping entities for another service's
  tables would create exactly the coupling the ownership rule forbids. Read-only
  SQL + a local `record` is the lighter contract.
- The DORA queries are shaped for one index; JPQL would fight that.
- All parameters are bound (`?` / `:named`) — no string concatenation of values,
  so no SQL injection. `ActivityQueryRepository.findDeployments` builds SQL
  dynamically but appends only **fixed clause text**, with values still bound.

File by file:
- **`ActivityQueryRepository`** — the four listing queries behind `/metrics/prs`,
  `/deployments`, `/workload`. Returns local `record` row types, never entities.
  `findReviews` / `findChecks` short-circuit on an empty id list, because
  `WHERE pr_id IN ()` is a SQL syntax error.
- **`DoraQueryRepository`** — one query: production deployment facts in a time
  range, left-joined to `commits` for the commit timestamp that lead time needs.
  Backed by the covering index added in `V6`.
- **`DoraSnapshotStore`** — reads and writes `dora_metrics`. The write is an
  **UPSERT** on `(project_id, calculated_date, window_days)`, which is a real
  unique constraint in `V1`. That makes the nightly job safe to re-run.
- **`EventReferenceRepository`** — translates *external* GitHub ids in an event
  into *internal* DevPulse ids. Every lookup tries the GitHub id first, then
  falls back to a direct internal id, because the event contract doesn't yet
  guarantee which one integration-service sends. This file is where the two
  services' id conventions get reconciled.
- **`ProjectScopeRepository`** — project lookup + repo count, the user's
  `system_role`, and the project-membership check. `findAllProjects()` exists only
  for the nightly scheduler, which runs across all tenants as a system job.

## Security (per-request identity)

### `security/RequestContext.java`
`record RequestContext(Integer userId, Integer companyId)` — the resolved caller.

### `security/RequestContextResolver.java`
Turns gateway headers into a `RequestContext`, or throws 401.
- Reads `X-User-Id` / `X-Company-Id`, with `X-DevPulse-*` accepted as a
  compatibility alias.
- Requires both, requires numeric, requires positive.
- **This service never parses a JWT.** It trusts the gateway's headers, and that
  is only sound because the gateway strips client copies of those headers on
  every request. The two files are one mechanism split across two services.

### `security/ProjectAccessService.java`
The authorization rule, in one place:
1. Project must exist **within the caller's company** → else 404 `PROJECT_NOT_FOUND`.
2. Caller must exist in that company → else 401.
3. Company **admin** sees everything; otherwise the caller must be a row in
   `project_members` → else 403 `PROJECT_ACCESS_DENIED`.

This is the per-project RBAC model made concrete: `Admin` is company-scoped,
membership is per-project. Note it enforces **view** access only — Manager vs
Developer isn't distinguished, because every endpoint here is read-only.

Ordering is deliberate: a project in another company returns **404, not 403**, so
the API can't be used to enumerate which project ids exist in other companies.

## Exceptions

- **`ApiException`** — carries an HTTP status *and* a stable machine-readable
  `code`, so the frontend can branch on `PROJECT_ACCESS_DENIED` rather than on
  prose.
- **`InvalidMetricEventException`** — thrown on the *message* path, not the HTTP
  path. It signals "this message is unprocessable", which combined with
  `default-requeue-rejected: false` sends it to the DLQ instead of poison-looping.
- **`GlobalExceptionHandler`** — `@RestControllerAdvice`. Three handlers:
  `ApiException` → its own status/code; validation failures → 400
  `INVALID_REQUEST`; anything else → **logged with a stack trace, but the client
  gets a generic `INTERNAL_ERROR` message**. Internal details never reach the
  wire.

## DTOs

`DeploymentResponse`, `DoraSummaryResponse`, `PullRequestResponse`,
`WorkloadEntryResponse`, `ErrorResponse` — all `record`s, the API contract.

Why they exist at all: **entities are never exposed.** Two concrete payoffs here —
ids are serialised as **strings** (so JS can't lose precision and the DB key type
can change without a frontend change), and `PullRequestResponse` composes
`reviews` + `checks` from other tables that no single entity models.

`DoraSummaryResponse` is the shape the dashboard needs in one round trip: current
value, unit, rating, **previous-period value** (for the trend arrow), sample size,
and a history series.

## Calculation (`service/calculation/`)

### `DoraMetricCalculator.java`
Strategy interface: `key()` + `calculate(facts, window)`. Four implementations,
each independently unit-testable, each addable without touching the others.

### The four calculators
- **`DeploymentFrequencyCalculator`** — successful production deployments ÷ window
  days. Higher is better.
- **`LeadTimeCalculator`** — mean hours from commit → successful deploy. Skips
  facts with no commit time, and skips ones where the commit is *after* the
  deploy (bad data), rather than producing a negative duration.
- **`MttrCalculator`** — mean hours from a failed/rolled-back deploy to its
  recovery. Only counts failures that **actually recovered**; an unrecovered
  failure has no measurable restore time and must not be treated as zero.
- **`ChangeFailureRateCalculator`** — failed+rolled-back ÷ completed. **Excludes
  `PENDING`**, because a deployment still in flight is not yet a success or a
  failure and would drag the rate down artificially.

Shared property worth pointing out: when there is no data they return
**`null`, not `0`** — with `sampleSize = 0`. A zero MTTR would read as "we recover
instantly", which is the opposite of the truth.

### `DoraRatingPolicy.java`
The ELITE/HIGH/MEDIUM/LOW thresholds, in **one** object so the bands can't drift
between the live endpoint and any future one. Two helpers because two metrics are
higher-is-better and two are lower-is-better. `null` → `NOT_AVAILABLE`.

## Services

### `service/MetricEventIngestionService.java` — the write path
The most defensive file in the service. Five `ingest` overloads, all
`@Transactional`. Everything here is about **idempotency and bad input**, because
webhooks are retried, arrive out of order, and are replayed.

- **Idempotent by external id**: every handler looks up the existing row by
  GitHub id (falling back to PR number) and updates it, so the same event
  delivered twice produces one row.
- **Ordering**: `pr.merged` / `pr.closed` for a PR that was never opened throws
  rather than inventing a half-populated row.
- **Multi-tenancy**: `commits.commit_sha` is a global PK in the current schema, so
  if a SHA already exists under a *different* company the event is rejected
  instead of silently overwriting another tenant's row. That is a schema
  limitation handled explicitly in code — good thing to raise proactively.
- **The deployment recovery state machine** is the subtle part:
  failed → success sets `failure_recovered_at` **without erasing the original
  failure status**, which is exactly what MTTR needs to measure. Success → failed
  clears the recovery and re-stamps `deployed_at`.
- **Normalisation**: GitHub's vocabulary (`failure`, `error`, `inactive`,
  `queued`, `in_progress`, `prod`, `stage`) is mapped onto the values the DB
  `CHECK` constraints allow. An unknown value throws rather than being coerced —
  bad data goes to the DLQ instead of into the metrics.
- Clamps negative line counts to 0 (the schema has `CHECK >= 0`), and caps SHA
  length at 40.

### `service/DoraMetricsService.java` — the read path
- Constructor takes `List<DoraMetricCalculator>` (Spring injects all four) and
  indexes them into an `EnumMap`, then **fails startup** if a key is duplicated or
  missing. Adding a fifth `DoraMetricKey` without a calculator is a boot failure,
  not a runtime `null`.
- `calculate()` fetches facts for **two windows in one query** — current and the
  immediately preceding period — so the trend comparison costs no extra round
  trip.
- `getSummary()` merges stored snapshots with today's live value into a single
  `TreeMap` keyed by date, so history is sorted and today is always present even
  before the nightly job runs.
- `toApiValue()` is the DB↔API unit boundary: change failure rate is stored as a
  **0..1 ratio** (schema `CHECK BETWEEN 0 AND 1`) and served as a **percentage**.
  One method owns that conversion.

### `service/ActivityMetricsService.java` — the listing endpoints
- Every method calls `accessService.requireViewAccess(...)` **first**. Authorization
  is not optional per-endpoint.
- Filter values (`environment`, `status`) are validated against an allow-list and
  rejected with `INVALID_FILTER` — not passed through to SQL.
- Avoids N+1: fetches PRs once, then reviews and checks in **one query each**, and
  groups them in memory.
- `getWorkload` computes load % against a configurable target (`4` active PRs) and
  mean cycle time from merged PRs. `durationHours` returns `null` for impossible
  intervals rather than a negative number.

### `service/DoraSnapshotScheduler.java`
Nightly cron (00:05 UTC) that writes one `dora_metrics` row per project, giving
the dashboard a real historical trend instead of only "right now".
- `@ConditionalOnProperty` so it can be switched off in dev/CI.
- Explicit `zone = "UTC"` — no daylight-saving surprises.
- **Catches per-project exceptions and continues.** One broken project must not
  stop the other 99 from getting a snapshot.
- Idempotent thanks to the store's UPSERT: re-running the job is safe.

## Consumer & controller (the two entrances)

### `consumer/MetricsEventConsumer.java`
Thin. `@RabbitListener` on the queue + `@RabbitHandler` per event type, each
delegating straight to the ingestion service — same "thin entrypoint, logic in
the service" rule as a controller. The `isDefault = true` handler logs and
**drops** unknown message types instead of throwing, so an unrelated event
routed here by mistake can't fill the DLQ.

### `controller/MetricsController.java`
Four `GET`s: `/dora`, `/prs`, `/deployments`, `/workload`. Thin — resolve context,
delegate, return a DTO. No logic.
- `@Validated` + `@Positive` / `@Min` / `@Max` on every parameter. The
  `@Max(500)` on `limit` and `@Max(365)` on `windowDays` are DoS guards: an
  unbounded `limit` is a way to ask the DB for everything.
- Sensible defaults (`windowDays=30`, `limit=100`) so the frontend can call the
  endpoint bare.
- Mapped at `/metrics`, reached as `/api/metrics/**` through the gateway's
  `StripPrefix=1`.

## Tests

- **`DoraCalculatorsTest`** — six cases covering each calculator's edge behaviour,
  including the two that matter most: metrics with no data are `null` **not** `0`,
  and the rating policy respects metric direction.
- **`MetricEventIngestionServiceTest`** — the two nastiest ingestion paths:
  success-after-failure marks recovery without erasing the failure, and a SHA
  owned by another tenant is rejected rather than overwritten.
- **`ProjectAccessServiceTest`** — admin sees a project without membership; a
  non-member is refused.
- **`RequestContextResolverTest`** — current headers, compatibility aliases, and
  the missing/non-numeric rejection.

All four run with no database and no broker, because of the injected `Clock` and
mocked repositories.

## Migration I own: `V6__extend_metrics_service_schema.sql`
Additive only — `IF NOT EXISTS` everywhere, wrapped in `BEGIN/COMMIT`. Adds
`github_pr_id` / `description` / `head_branch` / `url` / `updated_at` to
`pull_requests`, `users.avatar_url`, the `pr_checks` table,
`deployments.github_deployment_id`, `dora_metrics.calculated_at`, the unique
indexes that make ingestion idempotent, and one covering index shaped exactly for
the DORA query. Never edits `V1` — that's the rule.

---

# 3. analytics-service

Be upfront in the review: **the runtime service is a shell; the ML work is real
and lives in `training/`.** The two halves aren't connected yet.

## The service shell

### `app/main.py`
13 lines. FastAPI app with `/health`. The docstring states the intended contract:
consume `pr.*`, write `pr_predictions`, publish `alert.pr_high_risk`. None of that
is wired yet — say so plainly.

### `app/` subpackages (`api/`, `services/`, `ml/`, `consumers/`, `schemas/`, `database/`, `utils/`)
Empty `__init__.py` scaffolding. They exist to fix the layering **before** code
gets written, so features don't land in one 800-line file: routers in `api/`,
inference in `services/`, feature/model code in `ml/`, RabbitMQ listeners in
`consumers/`, Pydantic models in `schemas/`, SQLAlchemy in `database/`.

### `app/artifacts/`
Where the serialised model goes at runtime. `*.joblib` is gitignored — a model is
a build output, not source. Path comes from `ANALYTICS_MODEL_PATH`.

### `Dockerfile`
`COPY app ./app` **only**. Training code, datasets and notebooks never enter the
production image — a deliberate split, and the reason `training/` and `data/` are
siblings of `app/` rather than inside it.

### `requirements.txt`
Grouped by purpose with a comment block that says, in the file itself, **never add
Alembic here** — this service reads/writes only `pr_predictions` and must not
migrate the shared DB. Versions are currently unpinned; the stated policy is to
pin each one the first time we depend on its behaviour. Expect a reviewer to push
on this; it's a fair hit.

### `.env.local.example`
Note `POSTGRES_URL` here is a **SQLAlchemy DSN**, not JDBC — this is the one
Python service. Same shared database.

### `data/README.md` + `data/{raw,processed,samples}/`
The dataset policy: `raw/` untouched source dumps, `processed/` regenerable
feature tables, `samples/` a small committed slice so CI and teammates can run the
pipeline. `raw/` and `processed/` are gitignored because datasets are data, not
source, and everything in them must be reproducible.

## `training/` — the actual ML

The headline: **this is a leakage-control exercise, not a modelling exercise.**
If the review only takes one thing from analytics-service, it's this.

### `collect_pr_data.py` (458 lines)
Pulls PR history from the GitHub API into a flat table.
- Two phases: a cheap **LIST** phase (100 PRs/request, ~50 requests for 5000) and
  an expensive **DETAIL** phase, because the list endpoint omits `additions`,
  `deletions`, `changed_files` and `commits` — one extra request *per PR*. A full
  run costs ~5050 requests, i.e. the entire hourly quota for an authenticated
  token.
- Therefore: it **pauses when quota runs low and resumes at reset**, and appends
  every PR to `pr_cache.jsonl` so an interrupted run resumes instead of
  re-spending the quota. `--no-details` gives a one-minute run without diff-size
  features.
- Produces `pr_dataset.csv` with a 3-class `staleness_risk` label (Low <3d,
  Medium 3–14d, High >14d), bucketed from `resolution_days`.

### `train_model.py` (329 lines) — the first-generation trainer
3-class classifier over `pr_dataset.csv`. Three algorithms offered
(`xgboost`, `random_forest`, `logistic_regression`) **because the shared
`pr_predictions` table constrains `algorithm` to exactly those three values** —
the ML choices are bounded by the DB contract.

Its most valuable part is the `LEAKY` list and the comment above it, which works
out a distinction most people get wrong: "measured from now" is not the same as
"leaky". `time_since_created` is safe for closed PRs but for an **open** PR it is
*literally the label* (`resolution_days` = `now − created_at`, same arithmetic).
The `--keep-leaky` flag exists only to demonstrate the inflated score, never to
report one.

### `build_snapshots.py` (290 lines) — the fix
Rebuilds the dataset to answer the question production actually asks:

> *"It is T0. Knowing only what was true at T0, will this PR still be unresolved
> N days from now?"*

Three ideas to walk through:
1. **T0 = `created_at`, and why it has to be.** GitHub's pulls endpoint returns
   only the *current* state; the cache has no event timeline, so counters like
   `comments` and `additions` are collection-time values. "Snapshot at day 5"
   cannot be built honestly from this data. The open moment is the one instant
   whose state is reconstructible.
2. **Censoring.** A PR still open whose window hasn't fully elapsed has an
   *unknown* outcome. It is **excluded, never guessed** — labelling it "stale" is
   what corrupted the earlier dataset.
3. **`HistoryTracker`.** Author- and repo-level history where a prior PR counts
   only if it **resolved before T0**. Merely being *created* earlier isn't enough
   — its outcome would still have been unknown to an observer at T0. Uses
   `bisect` on a sorted list for the rolling 200-PR repo window. New authors get
   a `-1.0` sentinel rather than a silently-imputed average.

### `features_at_t0()` — the single most important function
Defined in `build_snapshots.py` and **imported by `predict.py`** rather than
reimplemented. A second copy of feature logic is the classic reason a model that
scored well offline makes nonsense predictions in production. One definition,
both paths.

### `train_snapshot_model.py` (350 lines) — the honest trainer
Binary classifier on `pr_snapshots.csv`. Built around one rule: nothing observed
after T0 may reach the model. Three consequences:
1. **Features** — every column is justified in the `LEAKAGE_AUDIT` table, in
   source. Anything that can't be defended there doesn't ship.
2. **The split is chronological (70/15/15), never random.** A random split would
   train on August and test on June — a situation that cannot occur in production.
3. **Preprocessing is inside the `Pipeline`**, fitted on the train fold only, so
   test-period statistics never leak into the imputer or the encoder.

It also keeps a `REMOVED_FEATURES` table — 20 dropped columns, each with the
reason it was dropped ("accumulates AFTER T0", "outcome itself", "future
timestamp"). That is deliberately kept in source so the decision is reviewable
instead of folklore. A `DummyClassifier` baseline is included so the reported
scores mean something.

### `predict.py` (142 lines)
Scores PRs with the trained bundle. Replays every *resolved* PR into a
`HistoryTracker` in chronological order — exactly as during training — then builds
each row through the shared `features_at_t0`. `--open` scores every still-open PR
in the cache, `--pr N` scores one.

### `training/README.md`
Full runbook: token setup, the time/quota budget, the label definition, and an
explicit **Leakage warning** section. Written so a teammate can reproduce the
dataset without asking me.

### `training/.gitignore`, `training/.env.example`
Model artefacts (`*.pkl`, `*.joblib`), datasets (`*.csv`, `*.jsonl`) and `.env`
are all ignored — verified. The `pr_dataset.csv` / `stale_pr_model.pkl` on my disk
are untracked regenerable outputs, not committed binaries.

---

# 4. Known soft spots — prepare answers, don't get surprised

Being first to name these usually goes better than being caught by them.

1. **Rate limiting is on exactly one route.** Only `auth-service` carries
   `RequestRateLimiter`. `projects`, `metrics`, `integration` and `notification`
   are unthrottled. Defensible (login brute-force is the real threat) but it
   should be a stated decision, not an oversight.
2. **The limiter fails open.** `RedisRateLimiter` catches Redis errors, logs
   *"Error determining if user allowed from redis"*, and **allows** the request.
   Redis down = no login throttle, silently. Worth a log alert on that string.
3. **`X-Company-Id` is the integration risk.** The gateway forwards it as an
   **empty string** when the JWT has no `companyId` claim; metrics-service
   **requires** it and 401s otherwise. If auth-service ships tokens without that
   claim, every metrics call fails with a generic 401 that looks like a token
   problem. This is downstream of the open JWT-shape decision and should be
   settled before the demo.
4. **`/actuator/**` entries in the public-path list are dead code.** Global
   filters only run for *matched routes*, so actuator paths never reach the JWT
   filter. Harmless, but a reviewer who knows gateway internals will spot it.
5. **Two comment inaccuracies in the gateway.** `JwtAuthenticationFilter` says
   routing is "order -1" (`RouteToRequestUrlFilter` is 10000), and
   `ApiGatewayApplicationTests`' javadoc points at
   `src/test/resources/application.yml` when the file is `application-test.yml`.
   Trivial, but easy points to concede gracefully.
6. **Analytics `requirements.txt` is unpinned.** Stated policy is to pin on first
   real dependency; a reviewer may reasonably want it pinned now.
7. **Two training pipelines exist.** `train_model.py` (legacy, 3-class) and
   `build_snapshots.py` + `train_snapshot_model.py` (point-in-time, binary). I
   should say clearly which is canonical — the snapshot pipeline is, and
   `predict.py` only uses that one — and whether the legacy trainer stays as the
   documented demonstration of the leakage effect or gets deleted.
8. **analytics-service isn't wired into anything.** No consumer, no
   `pr_predictions` writes, no `/api/predictions` route (it's commented out in the
   gateway), and the Docker image contains no model. The ML is finished; the
   integration is not.
9. **No integration tests touching a real database.** All metrics tests mock the
   repositories, so a wrong column name in the hand-written SQL would only surface
   at runtime. Testcontainers would close that gap.
