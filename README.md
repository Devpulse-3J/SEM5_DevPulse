# DevPulse

**An automated developer productivity and code quality insights dashboard for distributed engineering teams.** DevPulse ingests real-time events from GitHub, Jira, and Slack, computes recognised DORA metrics, predicts stale and high-risk pull requests using machine learning, and surfaces everything through a multi-tenant web dashboard so managers can answer "which PRs are stuck, who is overloaded, and why are releases slow?" without stitching together four disconnected tools.

---

## Table of Contents

1. [Problem Statement and Solution Overview](#problem-statement-and-solution-overview)
2. [Key Features](#key-features)
3. [Architecture Overview](#architecture-overview)
4. [Technology Stack](#technology-stack)
5. [Repository Structure](#repository-structure)
6. [Services in Detail](#services-in-detail)
7. [Data Flow Diagrams](#data-flow-diagrams)
8. [User Roles](#user-roles)
9. [Getting Started](#getting-started)
10. [Environment Variables](#environment-variables)
11. [Development Workflow](#development-workflow)
12. [API Documentation](#api-documentation)
13. [Contributing](#contributing)
14. [License and Authors](#license-and-authors)

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
- **Role-based access control (RBAC)** — four roles (Admin, Manager, Developer, Viewer) with scoped permissions enforced at the gateway and service layers.
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
| Flyway | Versioned database schema migrations |
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
| PostgreSQL | Primary datastore, one schema per service |
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

## Repository Structure

```
devpulse/
├── frontend/
│   ├── dashboard-app/          # Next.js dashboard for Manager, Developer, Viewer
│   ├── admin-app/              # Next.js admin console for org/tenant management
│   ├── shared-ui/              # shared component library used by both apps
│   └── shared-types/           # TypeScript types matching backend DTOs
│
├── backend/
│   ├── api-gateway/            # Spring Cloud Gateway — single entry point, routing, auth
│   ├── auth-service/           # users, orgs, JWT issuance, RBAC, multi-tenancy setup
│   ├── integration-service/    # GitHub & Jira webhook ingestion, event normalisation
│   ├── metrics-service/        # stores PRs/commits/deployments, exposes DORA metrics
│   ├── analytics-service/      # Python + FastAPI, ML predictions for stale/high-risk PRs
│   ├── notification-service/   # Slack, email, custom webhook alerts
│   └── shared-contracts/       # event schemas, OpenAPI specs, shared DTOs
│
├── infrastructure/
│   ├── docker/                 # Dockerfiles and config for Postgres, Redis, RabbitMQ
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
| `frontend/dashboard-app/` | Next.js app serving DORA dashboards and activity views for Managers, Developers, and Viewers |
| `frontend/admin-app/` | Next.js admin console for organisation creation, member invites, and integration management |
| `frontend/shared-ui/` | Reusable React component library (charts, tables, layout) shared by both frontend apps |
| `frontend/shared-types/` | TypeScript type definitions mirroring backend DTOs to keep the API contract type-safe |
| `backend/api-gateway/` | Spring Cloud Gateway; the only publicly exposed service — routes, authenticates, and rate-limits |
| `backend/auth-service/` | Owns users, organisations, sessions; issues and validates JWTs; sets up multi-tenancy and RBAC |
| `backend/integration-service/` | Receives GitHub and Jira webhooks, validates signatures, normalises them into internal events |
| `backend/metrics-service/` | Persists PRs, commits, and deployments; computes and exposes DORA metrics |
| `backend/analytics-service/` | Python/FastAPI service running ML models to score PRs for staleness and risk |
| `backend/notification-service/` | Dispatches alerts to Slack, email, and custom webhooks based on configured rules |
| `backend/shared-contracts/` | Language-neutral event schemas, OpenAPI specs, and shared DTO definitions |
| `infrastructure/docker/` | Dockerfiles and supporting config for PostgreSQL, Redis, and RabbitMQ |
| `infrastructure/kubernetes/` | Optional Kubernetes manifests for production deployment |
| `infrastructure/monitoring/` | Prometheus scrape configs and Grafana dashboard definitions |
| `scripts/` | Developer helper scripts for environment setup, database seeding, and resets |
| `docs/` | Architecture, API, and setup documentation beyond this README |
| `.github/workflows/` | GitHub Actions CI/CD pipeline definitions, one per service |

### Internal structure of a Java service

Every Java service follows a consistent internal layout. Not all folders exist in every service (e.g. only `auth-service` has `security/`, and only database-owning services have `db/migration/`).

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
| `resources/db/migration/` | Flyway SQL migration scripts that version the service's owned schema |

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

- **Purpose:** Identity, organisations, and access control.
- **Responsibilities:** Register and authenticate users; issue and refresh JWTs; manage organisations, invitations, and member roles; establish multi-tenant boundaries and RBAC policies.
- **Exposed endpoints (high-level):** `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `GET /orgs`, `POST /orgs/{id}/invitations`, `GET /users/me`.
- **Owned database:** `auth` schema — users, organisations, roles, invitations, refresh tokens.
- **Depends on:** PostgreSQL, Redis (session/token blacklist). Publishes `org.created` and `user.invited` events.

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

- **Purpose:** The primary analytics UI for Managers, Developers, and Viewers.
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
      ▼
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

| Role | Capabilities |
|---|---|
| **Admin** | Create and manage the organisation; invite and remove members; connect/disconnect GitHub and Jira integrations; control multi-tenant isolation and settings; full access to all dashboards |
| **Manager** | View team-wide DORA dashboards; configure alert rules and channels; monitor developer workload and Developer Experience scores; view all team activity |
| **Developer** | View personal and team activity; track own PRs and reviews; receive relevant alerts; read team dashboards |
| **Viewer** | Read-only access to dashboards and reports (for stakeholders); no configuration or write access |

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
cp .env.example .env
# edit .env with your secrets (see Environment Variables below)

# 3. Bring up the full stack (services + Postgres + Redis + RabbitMQ)
docker-compose up --build
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
| `POSTGRES_URL` | all DB-owning services | JDBC/DSN connection string to PostgreSQL |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | all DB-owning services | Database credentials |
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
