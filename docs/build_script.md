# OdinEye (DevPulse) — Build & Run Guide

**Written for:** anyone who needs to build the backend and run the system locally — with Docker, or natively.
**Scope:** the backend repo (Spring Boot services + FastAPI analytics + API gateway). The Next.js frontend lives in a separate repo.

> **Database note:** this project uses **one shared PostgreSQL database** (Supabase in the cloud). In the current compose, the local `postgres` and `flyway` containers are **commented out**, so services connect to Supabase using the `docker-compose.supabase.yml` override. The schema is owned by **Flyway migrations in `backend/database/migrations/`** — individual services never migrate the database (`spring.flyway.enabled=false`, `ddl-auto=validate`).

---

## 1. Prerequisites

| Tool | Needed for |
|---|---|
| Docker + Docker Compose | Running the stack (and infra like Redis/RabbitMQ). |
| Java 17 + Maven | Building/running the Java services natively. **No `./mvnw` is committed** — use `mvn` from `backend/`. |
| Python 3.11 | Running the analytics service natively. |
| Node.js | Only for the frontend (separate repo). |

---

## 2. One-time environment setup

The services read secrets from gitignored `.env.local` files (native) and the Docker `.env` (compose). Only `*.example` templates are committed.

```bash
# From the repo root.

# 1) Create each service's .env.local from its committed template:
for f in backend/*/.env.local.example; do cp "$f" "${f%.example}"; done

# 2) Create the Docker secrets file from its template, then edit it:
cp infrastructure/docker/.env.example infrastructure/docker/.env
#   -> open infrastructure/docker/.env and fill in the real values
#      (DB connection, RabbitMQ, SMTP, GitHub/Jira/Slack, JWT_SECRET, etc.)
```

> **Secrets stay out of git.** Only edit the real values in the gitignored `.env` / `.env.local` files. Do not commit them.

---

## 3. Run with Docker (recommended)

The stack is defined by **two** compose files, always used together — the base file plus the Supabase override (the override must come **second**):

```bash
cd infrastructure/docker

# Build and start the whole stack (services + Redis + RabbitMQ), against Supabase:
docker compose --env-file .env \
  -f docker-compose.yml \
  -f docker-compose.supabase.yml \
  up --build
```

Run it detached (in the background) with `-d`:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.supabase.yml up --build -d
```

Useful follow-ups:

```bash
# See what's running:
docker compose -f docker-compose.yml -f docker-compose.supabase.yml ps

# Tail logs for one service:
docker compose -f docker-compose.yml -f docker-compose.supabase.yml logs -f notification-service

# Stop everything:
docker compose -f docker-compose.yml -f docker-compose.supabase.yml down
```

### Just the infrastructure (Redis + RabbitMQ)
If you only want the infra up (e.g. to run services natively, see section 5):

```bash
cd infrastructure/docker
docker compose --env-file .env -f docker-compose.yml up -d redis rabbitmq
```

> Postgres and Flyway containers are commented out in the compose file — the database is Supabase. Do not expect a local `postgres` container to start.

---

## 4. Build the backend (without running)

The backend is a Maven **multi-module** project (parent `backend/pom.xml`). Always build from `backend/`.

```bash
cd backend

mvn package                        # build & test everything
mvn -pl <service> -am package      # build one service + the modules it depends on
mvn -pl <service> test             # run one service's tests

# Examples:
mvn -pl notification-service -am package
mvn -pl api-gateway test
```

> **Gotcha:** `mvn -pl <service> test -Dtest=ClassName` can fail in the reactor because of `shared-contracts`. Run the whole module's suite instead.

To get a Maven wrapper (optional, since none is committed):

```bash
cd backend && mvn -N wrapper:wrapper
```

---

## 5. Run natively (services on your machine)

Useful for fast iteration on one service while the infra runs in Docker.

### 5a. Start infra in Docker
```bash
cd infrastructure/docker
docker compose --env-file .env -f docker-compose.yml up -d redis rabbitmq
```

### 5b. Run a Java service
A native service reads `application.yml`, which defaults to `localhost` for Redis/RabbitMQ — so the Docker infra above is enough. For the database and other secrets, export the values yourself (nothing auto-loads `.env.local`):

```bash
cd backend
# export the env this service needs first (DB URL/user/password, JWT_SECRET, etc.)
mvn -pl api-gateway spring-boot:run
```

### 5c. Run the analytics service (Python)
```bash
cd backend/analytics-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

---

## 6. Ports & verifying it's up

| Service | Port | Health / docs |
|---|---|---|
| api-gateway | 8080 | `http://localhost:8080/actuator/health` (public entry) |
| auth-service | 8081 | `/actuator/health` |
| integration-service | 8082 | `/actuator/health` |
| metrics-service | 8083 | `/actuator/health` |
| notification-service | 8084 | `/actuator/health` |
| analytics-service | 8000 | `http://localhost:8000/docs` (FastAPI Swagger) |
| RabbitMQ | 5672 / 15672 | management UI at `http://localhost:15672` |
| Redis | 6379 | — |

All HTTP traffic goes through the **gateway on 8080**; the individual service ports are for local debugging only.

```bash
# Quick health check through the gateway:
curl http://localhost:8080/actuator/health
```

---

## 7. Database & migrations

- The schema lives **only** in `backend/database/migrations/` as Flyway files (`V<n>__description.sql`).
- Services must **not** migrate the DB: `spring.flyway.enabled=false`, `ddl-auto=validate`.
- Against Supabase, migrations are applied deliberately by the DB owner — **not** automatically by a service on startup.
- Never edit or renumber a migration that has already run; add a new, higher-numbered file.

---

## 8. Troubleshooting

**`./mvnw: not found`** — there is no Maven wrapper committed. Use `mvn` from `backend/`, or generate the wrapper (section 4).

**A service can't reach Redis/RabbitMQ when run natively** — make sure the infra containers are up (section 5a); native services default to `localhost`.

**A container has stale behaviour after an env change** — container env only refreshes on **recreate**, not restart. Re-run `up -d` (or `up --build`) rather than `restart`.

**Everything starts but can't reach the database** — check the DB values in `infrastructure/docker/.env`; the Supabase override expects them to be set. Do not `source` a `.env` whose values contain `&` in the shell; let compose read the file with `--env-file`.

**Postgres container didn't start** — expected. Postgres/Flyway are commented out; the DB is Supabase.
