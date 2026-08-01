# DevPulse

**An automated developer productivity and code quality insights dashboard for distributed engineering teams.** DevPulse ingests real-time events from GitHub, Jira, and Slack, computes recognised DORA metrics, predicts stale and high-risk pull requests using machine learning, and surfaces everything through a multi-tenant web dashboard so managers can answer "which PRs are stuck, who is overloaded, and why are releases slow?" without stitching together four disconnected tools.

---

## Table of Contents

1. [Problem Statement and Solution Overview](#problem-statement-and-solution-overview)
2. [Key Features](#key-features)
3. [Architecture Overview](#architecture-overview)
4. [Technology Stack](#technology-stack)
5. [Database](#database)
6. [Repository Structure](#repository-structure)
7. [Services in Detail](#services-in-detail)
8. [Data Flow Diagrams](#data-flow-diagrams)
9. [User Roles](#user-roles)
10. [Setup Guide — From Clone to First Commit](#setup-guide--from-clone-to-first-commit)
11. [Getting Started](#getting-started)
12. [Environment Variables](#environment-variables)
13. [Development Workflow](#development-workflow)
14. [API Documentation](#api-documentation)
15. [Contributing](#contributing)
16. [License and Authors](#license-and-authors)

---

## Problem Statement and Solution Overview

Modern software teams are distributed across time zones and rely on a fragmented toolchain: **GitHub** for source code and pull requests, **Jira** for tasks and bugs, and **Slack** for communication. The operational signals that tell an engineering manager how their team is really doing — pull requests waiting too long for review, individuals carrying an unbalanced load, deployment cadence, and the root causes of delayed releases — are scattered across these platforms with no shared view. Answering a simple question like "how long does a change take to reach production?" typically means manually cross-referencing three dashboards.

**DevPulse** is a centralised analytics platform that closes this gap. It:

- **Aggregates** real-time webhook events from GitHub and Jira into a single normalised event stream.
- **Computes** the four industry-recognised DORA metrics — Deployment Frequency, Lead Time for Changes, Mean Time to Recovery, and Change Failure Rate.
- **Predicts** which pull requests are likely to go stale or carry high merge risk, using ML models trained on historical PR patterns.
- **Alerts** teams through Slack, email, or custom webhooks the moment a bottleneck is detected.
- **Isolates** each organisation's data in a strict multi-tenant model, exposed through an interactive web dashboard with role-based access.

A first-time reader should think of DevPulse as an **event-driven data pipeline with an analytics layer on top**: webhooks come in, get normalised and fanned out to services that persist metrics and score risk, and the results flow back to a dashboard and an alerting system.

---

## Key Features

- **Webhook aggregation** — ingests and normalises GitHub and Jira webhook events into a unified schema.
- **DORA metrics** — first-class computation of Deployment Frequency, Lead Time for Changes, Mean Time to Recovery, and Change Failure Rate.
- **Predictive analytics** — ML models (Random Forest, Logistic Regression, XGBoost) score pull requests for staleness and merge risk.
- **Configurable alerts** — rule-based notifications delivered to Slack, email, or arbitrary custom webhooks.
- **Multi-tenancy** — every organisation's data is isolated; no cross-tenant leakage across metrics, analytics, or alerts.
- **Role-based access control (RBAC)** — three roles (Admin, Manager, Developer), with Manager and Developer granted **per project** rather than per user, enforced at the gateway and service layers.
- **Interactive dashboards** — DORA trends, developer workload, and Developer Experience scores rendered as charts.
- **Independently deployable services** — each backend service is containerised and owns its own database schema.

---

## Architecture Overview

DevPulse is a **monorepo** with a **microservices runtime architecture**. All source lives in one repository under two top-level application folders — `frontend/` and `backend/` — but at runtime each backend service is an independently containerised process that owns its own database schema (the **database-per-service** pattern). Services never reach into each other's tables.

Services communicate two ways:

- **Synchronously** via REST, always through the **API gateway**, which is the single public entry point. The gateway handles routing, JWT validation, and rate limiting before any request reaches an internal service.
- **Asynchronously** via **RabbitMQ**. When something happens (a webhook arrives, a risk score is computed), the originating service publishes an event; interested services consume it independently. This decoupling means the metrics, analytics, and notification services can each react to the same event without knowing about one another.

The following diagram shows how a single GitHub webhook (e.g. "pull request opened") flows through the system and back to the dashboard:

```
                              ┌──────────────────────┐
   GitHub / Jira  ──webhook──▶│  integration-service │
                              │  validate + normalise│
                              └───────────┬──────────┘
                                          │ publishes  pr.opened
                                          ▼
                              ╔══════════════════════╗
                              ║       RabbitMQ       ║
                              ╚═══════╦══════════╦════╝
                        consumes     ║          ║   consumes
                          ┌──────────┘          └──────────┐
                          ▼                                ▼
              ┌───────────────────┐              ┌────────────────────┐
              │  metrics-service  │              │  analytics-service │
              │  persist PR + DORA│              │  ML risk scoring   │
              └─────────┬─────────┘              └──────────┬─────────┘
                        │                                   │ if high risk:
                        │                                   │ publish
                        │                                   │ alert.pr_high_risk
                        │                                   ▼
                        │                        ┌────────────────────┐
                        │                        │ notification-svc   │
                        │                        │ Slack/email/webhook│
                        │                        └────────────────────┘
                        │
                        │  REST (polled through gateway)
                        ▼
              ┌───────────────────┐        ┌────────────────────┐
              │    api-gateway    │◀───────│   frontend (Next)  │
              │  routing + auth   │  REST  │   dashboard-app    │
              └───────────────────┘        └────────────────────┘
```

---

## Technology Stack

### Frontend

| Technology | Purpose in the System |
|---|---|
| React + Next.js (App Router) | Server- and client-rendered dashboard and admin UIs |
| Tailwind CSS | Utility-first styling across both apps |
| Recharts / Chart.js | Rendering DORA metric and workload visualisations |
| Redux Toolkit | Global client state management |
| React Query | Server-state fetching, caching, and background refresh |
| NextAuth.js | Session handling and auth integration with the backend |

### Backend — Java Services

| Technology | Purpose in the System |
|---|---|
| Java + Spring Boot | Runtime for all Java microservices |
| Spring Cloud Gateway | API gateway: routing, single entry point, edge auth |
| Spring Security + JJWT | Authentication, JWT issuance/validation, RBAC enforcement |
| Spring Data JPA (Hibernate) | ORM and repository layer over PostgreSQL |
| Flyway | Versioned schema migrations — run by ONE central owner (`backend/database/`), not per service |
| Spring AMQP | RabbitMQ producer/consumer integration |
| Springdoc OpenAPI | Auto-generated Swagger UI and OpenAPI specs |
| Maven | Build, dependency, and lifecycle management |
| JUnit 5 + Mockito | Unit and integration testing |

### Backend — Analytics Service

| Technology | Purpose in the System |
|---|---|
| Python + FastAPI | High-performance ML inference and prediction API |
| pandas, NumPy | Data wrangling and feature engineering |
| scikit-learn (Random Forest, Logistic Regression) | Baseline classification models for PR risk |
| XGBoost | Gradient-boosted model for high-risk PR prediction |
| pytest | Test suite for the analytics service |

### Infrastructure

| Technology | Purpose in the System |
|---|---|
| PostgreSQL | Single **shared** database (`devpulse`) for all services; deployed as infrastructure, not per service |
| Redis | Caching and rate-limit counters |
| RabbitMQ | Asynchronous event-driven messaging between services |
| Docker + Docker Compose | Local containerisation and orchestration |
| Kubernetes | Optional manifests for production deployment |
| Prometheus + Grafana | Metrics collection and dashboards |
| Spring Boot Actuator | Health, readiness, and metrics endpoints per Java service |

### DevOps

| Technology | Purpose in the System |
|---|---|
| GitHub Actions | CI/CD pipelines, per service |
| Docker Compose | Local dev orchestration of the full stack |
| Kubernetes manifests | Optional production deployment target |

---

## Database

DevPulse uses **one shared PostgreSQL database** (`devpulse`) for the whole backend.
Database-per-service is intentionally **not** used — for a 3-person project a single database
means simpler deployment and maintenance, and it allows the cross-domain foreign keys the schema
relies on (`pull_requests → users`, `dora_metrics → projects`, `pr_predictions → pull_requests`).
PostgreSQL is deployed as an infrastructure component (Docker locally; Neon/Supabase/RDS in the
cloud), not as a microservice.

**Logical table ownership.** Every table is physically in one database but logically owned by one
service. The rule: *a service may READ any table it needs, but WRITE only the tables it owns.*

| Service | Owns (writes) |
|---|---|
| auth-service | `companies`, `users`, `projects`, `project_members`, `integrations` |
| integration-service | `repos`, `jira_issues`, `raw_event_log` |
| metrics-service | `pull_requests`, `pr_reviews`, `commits`, `deployments`, `dora_metrics` |
| analytics-service | `pr_predictions` |
| notification-service | `alert_rules`, `alerts`, `notifications` |

**One migration owner.** The schema lives only in `backend/database/migrations/` as Flyway files
(`V<n>__description.sql`), applied by a single dedicated `flyway` container. Individual services
do not contain migrations and run with `spring.flyway.enabled=false`. Full detail — rationale,
naming conventions, and workflow — is in [`backend/database/README.md`](backend/database/README.md).

---

## Repository Structure

```
devpulse/
├── frontend/
│   ├── dashboard-app/          # Next.js dashboard for Managers and Developers
│   ├── admin-app/              # Next.js admin console for org/tenant management
│   ├── shared-ui/              # shared component library used by both apps
│   └── shared-types/           # TypeScript types matching backend DTOs
│
├── backend/
│   ├── api-gateway/            # Spring Cloud Gateway — single entry point, routing, auth
│   ├── auth-service/           # users, companies, projects, memberships, JWT issuance, RBAC
│   ├── integration-service/    # GitHub & Jira webhook ingestion, event normalisation
│   ├── metrics-service/        # stores PRs/commits/deployments, exposes DORA metrics
│   ├── analytics-service/      # Python + FastAPI, ML predictions for stale/high-risk PRs
│   ├── notification-service/   # Slack, email, custom webhook alerts
│   ├── shared-contracts/       # event schemas, OpenAPI specs, shared DTOs
│   └── database/               # shared DB: Flyway migrations (single owner), ERD, seeds
│       ├── migrations/         #   V1__create_tables.sql, V2__…  (the ONLY migration source)
│       ├── diagrams/           #   erd.dbml → erd.png
│       ├── seeds/              #   optional ad-hoc seed data
│       └── README.md           #   why one DB, table ownership, migration workflow
│
├── infrastructure/
│   ├── docker/                 # docker-compose.yml + postgres/ rabbitmq/ redis/ config
│   ├── kubernetes/             # optional k8s manifests
│   └── monitoring/             # Prometheus and Grafana configs
│
├── scripts/                    # dev helper scripts (setup, seed, reset)
├── docs/                       # architecture, API, and setup documentation
└── .github/workflows/          # CI/CD pipelines per service
```

### Folder reference

| Path | Purpose |
|---|---|
| `frontend/dashboard-app/` | Next.js app serving DORA dashboards and activity views, scoped to the projects the signed-in user belongs to |
| `frontend/admin-app/` | Next.js admin console for organisation creation, member invites, and integration management |
| `frontend/shared-ui/` | Reusable React component library (charts, tables, layout) shared by both frontend apps |
| `frontend/shared-types/` | TypeScript type definitions mirroring backend DTOs to keep the API contract type-safe |
| `backend/api-gateway/` | Spring Cloud Gateway; the only publicly exposed service — routes, authenticates, and rate-limits |
| `backend/auth-service/` | Owns users, companies, projects, project memberships, and sessions; issues and validates JWTs; sets up multi-tenancy and per-project RBAC |
| `backend/integration-service/` | Receives GitHub and Jira webhooks, validates signatures, normalises them into internal events |
| `backend/metrics-service/` | Persists PRs, commits, and deployments; computes and exposes DORA metrics |
| `backend/analytics-service/` | Python/FastAPI service running ML models to score PRs for staleness and risk |
| `backend/notification-service/` | Dispatches alerts to Slack, email, and custom webhooks based on configured rules |
| `backend/shared-contracts/` | Language-neutral event schemas, OpenAPI specs, and shared DTO definitions |
| `backend/database/` | Single source of truth for the shared database — Flyway migrations (the only migration owner), ERD, seeds, and the DB README |
| `infrastructure/docker/` | `docker-compose.yml` plus per-component config folders (`postgres/`, `rabbitmq/`, `redis/`) for the local stack |
| `infrastructure/kubernetes/` | Optional Kubernetes manifests for production deployment |
| `infrastructure/monitoring/` | Prometheus scrape configs and Grafana dashboard definitions |
| `scripts/` | Developer helper scripts for environment setup, database seeding, and resets |
| `docs/` | Architecture, API, and setup documentation beyond this README |
| `.github/workflows/` | GitHub Actions CI/CD pipeline definitions, one per service |

### Internal structure of a Java service

Every Java service follows a consistent internal layout. Not all folders exist in every service (e.g. only `auth-service` has `security/`). **No service contains a `db/migration/` folder** — all migrations are centralized in `backend/database/` (see [Database](#database)).

| Folder | Purpose |
|---|---|
| `config/` | Spring configuration classes — beans, RabbitMQ queues/exchanges, security config, CORS |
| `controller/` | REST controllers that expose HTTP endpoints and delegate to services |
| `service/` | Business logic layer; orchestrates repositories, mappers, and event publishing |
| `repository/` | Spring Data JPA repository interfaces for database access |
| `entity/` | JPA entities mapped to database tables (the persistence model) |
| `dto/` | Data Transfer Objects for request/response payloads and event bodies |
| `mapper/` | Converters between entities and DTOs (keeps the persistence model separate from the API) |
| `security/` | Authentication and authorisation components — JWT filters, RBAC rules (`auth-service` only) |
| `consumer/` | RabbitMQ event listeners that react to messages published by other services |
| `exception/` | Custom exceptions and global exception handlers for consistent error responses |

Schema migrations do **not** live in the service — they are added to `backend/database/migrations/`
and applied by the single Flyway owner. Each Java service runs with `spring.flyway.enabled=false`.

The **analytics-service** (Python) uses a FastAPI-idiomatic layout instead: `app/routers/` (endpoints), `app/services/` (inference logic), `app/models/` (trained/serialised ML models and training code), `app/schemas/` (Pydantic request/response models), and `tests/`.

---

## Services in Detail

### api-gateway

- **Purpose:** The single public entry point for all client traffic.
- **Responsibilities:** Route requests to the correct internal service; validate JWTs at the edge; enforce rate limiting (via Redis); aggregate downstream Swagger docs.
- **Exposed endpoints:** Reverse-proxied routes such as `/api/auth/**`, `/api/integrations/**`, `/api/metrics/**`, `/api/analytics/**`, `/api/notifications/**`.
- **Owned database:** None — the gateway is stateless.
- **Depends on:** `auth-service` (for token validation keys), Redis (rate limiting), and every downstream service it routes to.

### auth-service

- **Purpose:** Identity, companies, projects, and access control.
- **Responsibilities:** Register and authenticate users; issue and refresh JWTs; manage companies, projects, invitations, and project memberships; resolve a user's role for a given project; establish multi-tenant boundaries and RBAC policies.
- **Exposed endpoints (high-level):** `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `GET /users/me`, `GET /orgs`, `POST /orgs/{id}/invitations`, `GET /projects` (those visible to the caller), `POST /projects` and `DELETE /projects/{id}` (Admin only), `GET /projects/{id}/members`, `PUT /projects/{id}/members/{userId}/role` (Admin only — grants Manager or Developer).
- **Owned database:** `auth` schema — users, companies, projects, project memberships (the `(user, project) → role` table), invitations, refresh tokens.
- **Depends on:** PostgreSQL, Redis (session/token blacklist). Publishes `org.created`, `project.created`, and `user.invited` events.

### integration-service

- **Purpose:** The ingress point for external tool events.
- **Responsibilities:** Receive GitHub and Jira webhooks; validate webhook signatures/secrets; normalise heterogeneous payloads into a canonical internal event schema; publish normalised events to RabbitMQ.
- **Exposed endpoints:** `POST /webhooks/github`, `POST /webhooks/jira`, plus integration management (`GET /integrations`, `POST /integrations/github/connect`).
- **Published events:** `pr.opened`, `pr.merged`, `pr.closed`, `commit.pushed`, `deployment.created`, `issue.updated`.
- **Owned database:** `integration` schema — connected integrations, per-org secrets/tokens, raw event log.
- **Depends on:** PostgreSQL, RabbitMQ, `auth-service` (tenant resolution).

### metrics-service

- **Purpose:** The system of record for engineering activity and DORA metrics.
- **Responsibilities:** Consume normalised events; persist PRs, commits, and deployments per tenant; compute Deployment Frequency, Lead Time for Changes, MTTR, and Change Failure Rate; serve aggregated metrics to the dashboard.
- **Consumed events:** `pr.*`, `commit.pushed`, `deployment.created`, `issue.updated`.
- **Exposed endpoints (high-level):** `GET /metrics/dora`, `GET /metrics/prs`, `GET /metrics/workload`, `GET /metrics/deployments`.
- **Owned database:** `metrics` schema — pull requests, commits, deployments, computed metric snapshots.
- **Depends on:** PostgreSQL, RabbitMQ.

### analytics-service

- **Purpose:** Predictive intelligence over historical PR data.
- **Responsibilities:** Consume PR events; run ML models to score PRs for staleness and merge risk; persist risk scores; publish high-risk alerts.
- **Consumed events:** `pr.opened`, `pr.updated`.
- **Published events:** `alert.pr_high_risk`.
- **Exposed endpoints (high-level):** `GET /analytics/predictions`, `GET /analytics/prs/{id}/risk`, `POST /analytics/retrain` (admin).
- **Owned database:** `analytics` schema — feature snapshots, risk scores, model metadata.
- **Depends on:** PostgreSQL, RabbitMQ. Built with Python + FastAPI.

### notification-service

- **Purpose:** Alert delivery.
- **Responsibilities:** Store per-org alert rules and channels; consume alert events; evaluate rules; dispatch notifications to Slack, email, or custom webhooks; track delivery status.
- **Consumed events:** `alert.pr_high_risk`, and other `alert.*` events.
- **Exposed endpoints (high-level):** `GET /notifications/rules`, `POST /notifications/rules`, `GET /notifications/channels`, `GET /notifications/history`.
- **Owned database:** `notification` schema — alert rules, channels, delivery history.
- **Depends on:** PostgreSQL, RabbitMQ, external services (Slack API, SMTP, target webhooks).

### shared-contracts

- **Purpose:** The shared source of truth for cross-service contracts.
- **Responsibilities:** Define RabbitMQ event schemas, OpenAPI specifications, and shared DTOs so Java and Python services agree on payload shapes.
- **Owned database:** None — this is a contracts/definitions module, not a runtime service.

### frontend/dashboard-app

- **Purpose:** The primary analytics UI for Managers and Developers, scoped to a selected project.
- **Responsibilities:** Render DORA trend charts, developer workload, PR/review activity, and Developer Experience scores; poll `metrics-service` and `analytics-service` through the gateway; surface received alerts.
- **Depends on:** `api-gateway` (all data), `shared-ui`, `shared-types`, NextAuth.js sessions.

### frontend/admin-app

- **Purpose:** The administrative console.
- **Responsibilities:** Create and manage organisations; invite and manage members; connect/disconnect GitHub and Jira integrations; manage multi-tenant settings.
- **Depends on:** `api-gateway` → `auth-service` and `integration-service`, `shared-ui`, `shared-types`.

---

## Data Flow Diagrams

### (a) Webhook ingestion → dashboard update

```
GitHub ──"PR opened" webhook──▶ integration-service
                                     │ 1. validate signature
                                     │ 2. normalise payload
                                     │ 3. publish pr.opened
                                     ▼
                                 RabbitMQ
                        ┌────────────┴────────────┐
                        ▼                         ▼
                 metrics-service           analytics-service
                 persist PR record         run ML risk model
                 update DORA metrics       store risk score
                        │
                        │  (later) frontend polls via gateway
                        ▼
                 api-gateway ──▶ dashboard-app renders updated metrics
```

### (b) Authentication flow through the gateway

```
Browser (login form)
      │  POST /api/auth/login  (email + password)
      ▼
 api-gateway ──route──▶ auth-service
      │                     │ verify credentials
      │                     │ issue signed JWT (+ refresh token)
      │◀────────────────────┘
      │  200 OK  { accessToken, refreshToken }
      ▼haring

Browser stores session (NextAuth.js)
      │
      │  subsequent request: GET /api/metrics/dora
      │  Authorization: Bearer <accessToken>
      ▼
 api-gateway ──validate JWT (edge)──▶ metrics-service
      │  (rejects with 401 if invalid/expired)
      ▼
 metrics-service returns tenant-scoped data
```

### (c) Alert generation and dispatch

```
analytics-service
      │  risk score computed for PR
      │  score >= threshold ?
      │        │ yes
      │        ▼
      │  publish alert.pr_high_risk ──▶ RabbitMQ
      │                                     │
      │                                     ▼
      │                            notification-service
      │                                 │ 1. match against org alert rules
      │                                 │ 2. resolve channels
      │                                 ▼
      │                     ┌───────────┼───────────┐
      │                     ▼           ▼           ▼
      │                  Slack        Email     Custom webhook
      │                     └───────────┼───────────┘
      │                                 ▼
      │                        record delivery status
```

---

## User Roles

DevPulse has **three roles**. The critical rule is that **Manager and Developer are not
properties of a user — they are properties of a membership**. A role is granted per project,
so the same person can own one project as its Manager while contributing code to another as a
Developer. A Manager writes code too; owning a project adds responsibility, it does not
replace the day job.

```
Company (tenant)
  ├── Admin ......................... company-scoped role, held by the user directly
  └── Project A          Project B
        │                   │
        └── Sara: Manager   └── Sara: Developer     ← same user, different role per project
            Ravi: Developer     Ravi: Manager
```

| Role | Scope | Capabilities |
|---|---|---|
| **Admin** | Company | Creates and deletes projects; grants and revokes the Manager role on a project; invites and removes company members; connects/disconnects GitHub and Jira integrations; manages company settings; can view every project in the company |
| **Manager** | Per project they own | Everything a Developer can do on that project, **plus**: manages the project's team and settings, configures its alert rules and channels, and views project-wide DORA dashboards, developer workload, and Developer Experience scores |
| **Developer** | Per project they belong to | Writes the code being measured; views their own and the project's activity; tracks their own PRs and reviews; receives relevant alerts; reads the project's dashboards |

**Enforcement rules:**

- Only an **Admin** may create or delete a project, or grant the Manager role. A Manager
  cannot promote another user or create a project.
- A **Manager**'s authority is bounded by the specific project they own. Owning Project A
  grants no elevated access to Project B — if they are a Developer there, they are treated
  purely as a Developer there.
- A **Developer** sees only projects they are a member of.
- No role may see across companies — tenant isolation is absolute, enforced at the gateway
  and again in each service.

**Implementation consequence:** permission checks are always evaluated as
`(user, project) → role`, never `user → role`. A JWT therefore cannot carry one global role
claim; it carries the user's identity, their company, and their company-level flag (admin or
member), while the per-project role is resolved from the membership table on each request.
`auth-service` owns that table.

---

## Setup Guide — From Clone to First Commit

> **Current repository state:** Phase 0 is **done** — the backend is a Maven multi-module project
> and each service builds. What's left is feature code inside the (empty) service packages. The
> commands in [Getting Started](#getting-started) work.

### Step 0 — Install prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 17+ | All Java services and Maven builds |
| Python | 3.11+ | analytics-service |
| Docker Desktop | latest, with Compose | Postgres, Redis, RabbitMQ, and building the services |
| Git | any recent | Version control |
| Maven | 3.9+ (optional) | Native Java builds; not needed if you build via Docker |

Verify with `java -version`, `python --version`, `docker compose version`.

### Phase 0 — bootstrap (DONE)

The one-time bootstrap is complete and merged. For reference, it delivered:

- **Maven multi-module build** — parent `backend/pom.xml` (Spring Boot 3.3.5, Java 17, Spring
  Cloud 2023.0.3) aggregating `shared-contracts` and the five Java services, each with its own
  `pom.xml`.
- **Maven layout** — sources under `backend/<service>/src/main/java/com/devpulse/<pkg>/`,
  `application.yml` under `src/main/resources/`, tests under `src/test/java/…`. No `db/migration/`
  in any service (migrations are centralized in `backend/database/`).
- **Entrypoints** — a `@SpringBootApplication` per Java service.
- **`analytics-service`** — a runnable FastAPI package (`app/main.py`, `app/__init__.py`).
- **Dockerfiles** — one per service; Java services build via context `backend/` (multi-module).
- **Infra** — `infrastructure/docker/docker-compose.yml` (full stack + dedicated `flyway`
  migration service), `.env.example`, and the shared-database schema in `backend/database/`.

> **Note on the Maven wrapper:** `./mvnw` is not committed (the bootstrap machine had no Maven).
> Build via Docker (recommended, no local Maven needed) or generate the wrapper once with
> `cd backend && mvn -N wrapper:wrapper` if you have Maven installed.

### Step 1 — Configure your environment

Every service ships a committed `.env.local.example`. Copy each one to `.env.local` (which is
gitignored) and fill in the placeholders:

```bash
for f in backend/*/.env.local.example frontend/*/.env.local.example; do
  cp "$f" "${f%.example}"
done
```

Ports are already assigned and do not collide:

| Service | Port |
|---|---|
| api-gateway | 8080 |
| auth-service | 8081 |
| integration-service | 8082 |
| metrics-service | 8083 |
| notification-service | 8084 |
| analytics-service | 8000 |
| dashboard-app | 3000 |
| admin-app | 3001 |

**Never commit a `.env.local`.** Only the `.example` templates are tracked. If you add a new
variable, add it to the template *and* to [Environment Variables](#environment-variables).

### Step 2 — Start the infrastructure and apply migrations

Copy the Docker secrets file, then bring up Postgres, Redis, RabbitMQ, and the one-shot `flyway`
migration service (it applies `backend/database/migrations/` to the shared `devpulse` database and
exits):

```bash
cp infrastructure/docker/.env.example infrastructure/docker/.env   # edit secrets
docker compose -f infrastructure/docker/docker-compose.yml up -d postgres redis rabbitmq flyway
docker compose -f infrastructure/docker/docker-compose.yml logs flyway   # confirm migrations applied
```

Confirm RabbitMQ is healthy at `http://localhost:15672` before starting any service. (The full
`up` including service containers won't work until each service has a Dockerfile.)

### Step 3 — Run only what you're working on

You do not need the full stack. Run the infrastructure in Docker and just your service natively
for a fast inner loop — see [Getting Started](#getting-started) for the per-service commands.
A useful minimum for frontend work is: infrastructure + `auth-service` + `api-gateway`.

### Step 4 — Follow the folder conventions when you code

Put each file in the layer it belongs to; this is what keeps the services consistent:

| Writing… | Goes in |
|---|---|
| An HTTP endpoint | `controller/` — keep it thin, delegate to `service/` |
| Business logic | `service/` |
| A database query | `repository/` |
| A table mapping | `entity/` |
| A request/response payload | `dto/` — never expose an `entity` over HTTP |
| Entity ↔ DTO conversion | `mapper/` |
| A RabbitMQ listener | `consumer/` |
| A schema change | `backend/database/migrations/` as a new `V<n>__description.sql` (central Flyway owner — never inside a service) |

Cross-service contracts (event schemas, shared DTOs) live in `backend/shared-contracts/` and
their TypeScript mirrors in `frontend/shared-types/` — **update both together**, or the
frontend and backend silently drift apart.

### Step 5 — Branch, commit, and open a PR

```bash
git checkout -b feature/<short-description>
```

Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat(metrics): ...`),
keep changes scoped to one service where possible, add tests for any behaviour change, and
confirm the service is green (`./mvnw test` / `pytest` / `npm test`) before opening the PR.

### Settled architecture decisions

- **Database topology — DECIDED: one shared database.** All services connect to a single
  PostgreSQL database `devpulse`. Database-per-service is intentionally not used. See
  [Database](#database) and `backend/database/README.md`.
- **Migrations — DECIDED: one Flyway owner.** Migrations live only in `backend/database/migrations/`
  and are applied by the dedicated `flyway` container. Every service sets
  `spring.flyway.enabled=false`; the Python service carries no migration tool.
- **JWT role claim — STILL OPEN.** Because roles are per-`(user, project)`, the token can't carry
  one global role claim. Decide (per-request membership lookup vs. embedded project→role map)
  before building `auth-service`.

---

## Getting Started

### Prerequisites

- **JDK 17+** (for the Java services and Maven builds)
- **Node.js 20+** (for the Next.js frontends)
- **Python 3.11+** (for the analytics service)
- **Docker Desktop** (with Docker Compose)

### Clone and run with Docker Compose

```bash
# 1. Clone the repository
git clone <your-repo-url> devpulse
cd devpulse

# 2. Copy environment templates and fill in placeholders
cp infrastructure/docker/.env.example infrastructure/docker/.env
# edit it with your secrets (see Environment Variables below)

# 3. Bring up the full stack (services + Postgres + Redis + RabbitMQ + Flyway migrations)
docker compose -f infrastructure/docker/docker-compose.yml up --build
```

Once the stack is healthy:

- Dashboard app: `http://localhost:3000`
- Admin app: `http://localhost:3001`
- API gateway: `http://localhost:8080`
- RabbitMQ management UI: `http://localhost:15672`
- Grafana: `http://localhost:3002`

### Running an individual service natively (for development)

Any service can be run on its own against the Dockerised infrastructure (Postgres, Redis, RabbitMQ) for a faster inner loop:

```bash
# Java service (example: metrics-service)
cd backend/metrics-service
./mvnw spring-boot:run

# Analytics service (Python)
cd backend/analytics-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload

# Frontend app
cd frontend/dashboard-app
npm install
npm run dev
```

---

## Environment Variables

Each service reads configuration from environment variables. The table below lists the main ones; use `<placeholder>` values in your local `.env` and never commit real secrets.

| Variable | Service(s) | Purpose |
|---|---|---|
| `POSTGRES_URL` | all DB-using services | Connection string to the single shared `devpulse` database (JDBC for Java, DSN for Python) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | all DB-using services | Shared database credentials |
| `SPRING_FLYWAY_ENABLED` | all Java services | Must be `false` — migrations are run only by the central `backend/database` Flyway owner |
| `JWT_SECRET` | auth-service, api-gateway | Signing/validation key for JWTs |
| `JWT_EXPIRATION` | auth-service | Access token lifetime |
| `GITHUB_WEBHOOK_SECRET` | integration-service | HMAC secret to verify GitHub webhook signatures |
| `JIRA_WEBHOOK_SECRET` | integration-service | Secret to verify Jira webhook payloads |
| `JIRA_BASE_URL` | integration-service | Base URL of the connected Jira instance |
| `JIRA_API_TOKEN` / `JIRA_EMAIL` | integration-service | Jira API credentials |
| `RABBITMQ_URL` | all services | AMQP connection string for RabbitMQ |
| `REDIS_URL` | api-gateway, auth-service | Cache and rate-limit store connection |
| `SLACK_WEBHOOK_URL` | notification-service | Default Slack incoming webhook for alerts |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASSWORD` | notification-service | Email delivery credentials |
| `ANALYTICS_MODEL_PATH` | analytics-service | Path to the serialised ML model artifact |
| `RISK_THRESHOLD` | analytics-service | Score at/above which a PR triggers a high-risk alert |
| `NEXTAUTH_SECRET` | frontend apps | NextAuth.js session encryption secret |
| `NEXT_PUBLIC_API_BASE_URL` | frontend apps | Public base URL of the API gateway |

---

## Development Workflow

### Branching

- `main` — always deployable.
- `develop` — integration branch (optional, depending on team preference).
- Feature branches: `feature/<short-description>`
- Fixes: `fix/<short-description>`

### Commit conventions

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(metrics): add change failure rate computation
fix(auth): reject expired refresh tokens
docs(readme): document alert dispatch flow
```

### Running tests per service

```bash
# Java services (JUnit 5 + Mockito)
cd backend/<service>
./mvnw test

# Analytics service (pytest)
cd backend/analytics-service
pytest

# Frontend apps
cd frontend/<app>
npm test
```

Open a pull request against `main` (or `develop`); CI runs the relevant service's pipeline from `.github/workflows/` before review.

---

## API Documentation

Every Java service documents its own API with **Springdoc OpenAPI** and exposes an interactive Swagger UI at:

```
/swagger-ui.html
```

The **analytics-service** (FastAPI) serves its interactive docs at `/docs` (Swagger UI) and `/redoc`.

Per-service specs are aggregated through the **api-gateway**, so consumers can browse the full API surface from a single origin rather than visiting each service directly. Shared DTO and event schemas live in `backend/shared-contracts/`.

---

## Contributing

- Pick up or open an issue before starting significant work.
- Branch from `main`, keep changes scoped to one service where possible, and follow the internal folder conventions described above.
- Add or update tests for any behaviour change; keep the service green (`mvnw test` / `pytest` / `npm test`).
- Update `shared-contracts/` and `shared-types/` together whenever an API or event schema changes, so the frontend and backend stay in sync.
- Write clear Conventional Commit messages and a descriptive PR body.
- Do not commit secrets; use `.env` and the placeholder table above.

---

## License and Authors

**License:** `<license placeholder>` (e.g. MIT) — see `LICENSE`.

**Authors:**

- `<Your Name>` — `<role / responsibility>`
- `<Team member>` — `<role / responsibility>`

DevPulse — semester project. Built as a demonstration of event-driven microservices, DORA analytics, and applied ML for engineering productivity.
