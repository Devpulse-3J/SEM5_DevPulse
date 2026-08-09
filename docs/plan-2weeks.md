# DevPulse — 2-Week Backend Plan

Ownership follows [separation.md](separation.md); contracts follow [api.md](api.md).
10 working days, 3 people. The goal is **the frontend running entirely on real backend data**,
not a complete product.

## The one decision that shapes everything

The frontend is finished and expects ~20 endpoints. Building live GitHub/Jira ingestion, DORA
computation from real events, and a trained ML model *and* wiring the API in two weeks is not
achievable for three people. So:

> **Every endpoint is real — real controllers, real services, real JPA queries, real database
> rows. What is deferred is the ingestion that fills those rows.** Data comes from a rich seed
> migration (`V5`) instead of live webhooks.

This is defensible in a report ("the ingestion pipeline is designed and partially built; the API
and data model are complete") and it is the only version that finishes. Say so explicitly rather
than letting it look unfinished.

---

## Day 1 — everyone, together (blocking)

Nothing parallelises until these three land, in this order:

1. **All three, 1 hour:** read [api.md](api.md) together. Settle section 7 (do we keep the
   frontend's presentation fields — recommendation: yes). Settle the `/api` prefix + StripPrefix.
2. **Didula, then everyone waits:** write and apply `V4__align_schema_with_api_contract.sql`.
   Roughly 20 columns and 3 new tables. **Nobody can write a JPA entity before this exists** —
   `ddl-auto: validate` crashes on any mismatch.
3. **Didula:** `V5__seed_demo_data_v2.sql` — 3 repos, ~40 PRs across 8 weeks with reviews and
   checks, ~30 deployments, ~20 predictions, alert rules and alerts. Everyone else's endpoints
   return data because of this file, so it is worth doing properly.

Migration numbers are assigned by Didula. Kalhara and Umaya do **not** create their own `V<n>`.

While waiting on V4, Kalhara and Umaya can write DTOs and mappers — those depend on
[api.md](api.md), not on the schema.

---

## Didula — gateway, analytics, platform

| Days | Work |
|---|---|
| 1 | `V4` + `V5` migrations (**blocks everyone — do first**) |
| 2–3 | Gateway: routes, CORS, `JwtAuthenticationFilter`, error envelope ([api.md](api.md) §4). Verify with the three curl checks. |
| 4 | Wire compose: gateway env for all five downstream URIs, confirm full `docker compose up --build` works |
| 5–7 | analytics-service: SQLAlchemy layer over `pr_predictions`, `GET /analytics/prs/{id}/risk`, offline training script (`app/ml/`) producing a `.joblib`, and a scoring pass over the seeded PRs |
| 8 | CI workflow (`mvn -B verify` from `backend/`, pytest, Flyway smoke-test against a throwaway DB) |
| 9 | Deploy: GHCR images, `docker-compose.prod.yml`, one server, first real deployment |
| 10 | Buffer, demo script, README/report updates |

**Watch out:** the gateway is on the critical path for the frontend but you cannot fully test it
until Kalhara's auth-service issues a real token. Agree the JWT claim names (`sub`, `companyId`,
`systemRole`) with Kalhara on day 1 and build against a hand-signed token — do not wait.

---

## Kalhara — auth-service + metrics-service (heaviest load)

| Days | Work |
|---|---|
| 2–3 | auth-service entities (`companies`, `users`, `projects`, `project_members`) + repositories. `Integer` ids, `OffsetDateTime` for `timestamptz`, field names `companyId`/`userId` so they map to `company_id`/`user_id`. |
| 3–4 | `POST /auth/login`, `/auth/register`, `GET /users/me` with BCrypt + JJWT. `/users/me` must include memberships, and **synthesise `ADMIN` memberships for company admins** ([api.md](api.md) §5.1). |
| 5 | `GET /orgs`, `GET /orgs/{id}/invitations`, project list with `memberCount` |
| 6–8 | metrics-service: entities, then `GET /metrics/prs`, `/metrics/dora`, `/metrics/workload`, `/metrics/deployments` — all reading seeded rows |
| 9 | DORA computation as real SQL over `deployments`/`pull_requests` rather than reading `dora_metrics` verbatim, if time allows |

**You are the bottleneck.** Two services and ~11 endpoints is more than the others carry. If you
are behind at day 6, hand `/metrics/deployments` and `/metrics/workload` to Umaya — those are
read-only queries and cross-service *reads* are allowed.

**Do not** build refresh-token storage, RBAC annotations, or `/auth/refresh` revocation. Issue a
stateless long-lived second token and move on.

---

## Umaya — integration-service + notification-service

| Days | Work |
|---|---|
| 2 | integration-service entities (`repos`, `raw_event_log`, `repo_branches` from V4) |
| 3–4 | `GET /repositories?projectId=`, `GET /repositories/{id}` with `branches[]` + `recentCommits[]`, `POST /repositories/{id}/sync` (202, updates `last_sync_at`) |
| 5 | `GET /integrations`, `POST /integrations/github/connect` (returns the OAuth URL — completing the flow is out of scope) |
| 6–7 | notification-service entities + `GET /notifications/rules`, `/history`, `/channels` |
| 8 | `POST /webhooks/github`: HMAC verification + write to `raw_event_log`. **Test against port 8082 directly** — the gateway route exists but webhooks carry no JWT, so they must be on the public-paths list. |
| 9 | One real Slack delivery on one alert rule. Email and custom webhooks: out of scope. |
| 10 | Help Kalhara |

**Two schema traps:** `raw_event_log.payload` is `jsonb` — needs `@JdbcTypeCode(SqlTypes.JSON)` on
Hibernate 6. And `raw_event_log.company_id` is `NOT NULL`, so you must resolve the company from
`repos.github_repo_id` *before* inserting; decide today what happens to a webhook from an
unknown repo (recommendation: `202` and drop it).

You finish earliest — day 10 is deliberately reserved to absorb Kalhara's overflow.

---

## Checkpoints

**Day 4, 30 minutes, all three.** Frontend logs in through the gateway against real auth-service,
no CORS errors, `/users/me` returns real memberships. If this fails, stop and fix it — everything
downstream assumes it.

**Day 8, 1 hour, all three.** Every endpoint in [api.md](api.md) returns real data. Frontend runs
with mocks switched off, end to end. Anything still mocked at this point gets cut, not finished.

**Day 9.** First production deploy. If the deploy is not green by end of day 9, demo from local
Docker — do not spend day 10 debugging infrastructure.

---

## Rules that keep three people from colliding

- **Write only your own tables** ([separation.md](separation.md)). Reading any table is fine —
  integration-service counting open PRs is allowed; writing them is not.
- **Migrations go through Didula.** One `V<n>` sequence, no exceptions.
- **`api.md` is the contract.** If a shape has to change, change `api.md` in the same PR and tell
  the other two — a silent rename breaks the frontend at runtime, not at compile time.
- **Branch per feature off `main`**, Conventional Commits scoped to your service
  (`feat(auth): add login endpoint`), reviewed by one other member.
- **Note it in the report when you cut something.** Scope cuts that are written down read as
  engineering judgement; the same cuts undocumented read as an unfinished project.
