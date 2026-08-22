# DevPulse — Testing & Health Checks

How to run each service, confirm it is healthy, and run its tests.

Every port and endpoint below is read from the actual `application.yml` and
controller sources, not assumed.

---

## Services at a glance

| Service | Port | Database | RabbitMQ | Other |
|---|---|---|---|---|
| `api-gateway` | 8080 | — none | — | **Redis** (rate limiter) |
| `auth-service` | 8081 | ✅ Postgres | ✅ (health check disabled) | JWT signing |
| `integration-service` | 8082 | ✅ Postgres | ✅ publishes | GitHub/Jira webhook secrets |
| `metrics-service` | 8083 | ✅ Postgres | ✅ | — |
| `notification-service` | 8084 | ✅ Postgres | ✅ consumes | SMTP, Slack, webhooks |
| `analytics-service` | 8000 | ✅ Postgres (`pr_predictions` only) | ✅ consumes | Python / FastAPI |

`SERVER_PORT` overrides the port on every Java service except the gateway,
which is fixed at 8080.

---

## Running tests

There is **no `./mvnw` wrapper** committed in this repo. Run Maven from
`backend/`.

```bash
cd backend

mvn test                                   # every module
mvn verify                                 # compile + test + package

mvn -pl auth-service -am test              # one service (and its dependencies)
mvn -pl auth-service test -Dtest=AuthControllerTest
mvn -pl auth-service test -Dtest=AuthControllerTest#loginReturns200AndToken

# Only the context smoke tests, across all services.
mvn test -Dtest='*ApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false
```

**No infrastructure is required to run the tests.** Every test runs against an
in-memory H2 database with RabbitMQ listeners disabled, configured in each
service's `src/test/resources/application-test.yml` under the `test` profile.
Production configuration is untouched — Postgres and Flyway still own the real
schema.

### Analytics service (Python)

```bash
cd backend/analytics-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

pytest tests/                              # all tests
pytest tests/test_x.py::test_name          # one test
```

---

## Running a service

Java services read `application.yml`, which defaults to `localhost` for
Postgres, Redis and RabbitMQ. Values in `.env.local` are **not** auto-loaded;
export them yourself or pass them inline.

```bash
cd backend

# Natively, one service at a time
JWT_SECRET=<32+ chars> mvn -pl api-gateway spring-boot:run
JWT_SECRET=<32+ chars> mvn -pl auth-service spring-boot:run

# Or from the built jar
mvn -pl api-gateway -am package -DskipTests
JWT_SECRET=<32+ chars> java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
```

`JWT_SECRET` must be **at least 32 characters** — the gateway's `JwtService`
rejects anything shorter at construction — and must be **byte-identical**
between `api-gateway` and `auth-service`, since HS256 is symmetric. A mismatch
looks like an ordinary 401.

```bash
# Analytics service
cd backend/analytics-service && uvicorn app.main:app --reload
```

---

## Health checks

Every Java service exposes exactly two actuator endpoints: `health` and `info`.
Sensitive endpoints (`env`, `beans`, `shutdown`, `heapdump`, `threaddump`) are
**not** exposed — they return 404.

```bash
curl http://localhost:8080/actuator/health    # api-gateway
curl http://localhost:8081/actuator/health    # auth-service
curl http://localhost:8082/actuator/health    # integration-service
curl http://localhost:8083/actuator/health    # metrics-service
curl http://localhost:8084/actuator/health    # notification-service
curl http://localhost:8000/health             # analytics-service (FastAPI, not actuator)
```

`show-details: always` is set, so the response names which component is
failing rather than only an aggregate status:

```json
{
  "status": "UP",
  "components": {
    "db":     { "status": "UP", "details": { "database": "PostgreSQL" } },
    "rabbit": { "status": "UP", "details": { "version": "3.13.x" } },
    "redis":  { "status": "UP", "details": { "version": "7.4.9" } },
    "diskSpace": { "status": "UP" },
    "ping":   { "status": "UP" }
  }
}
```

A `DOWN` status with `"db"` missing or failing means the service cannot reach
Postgres. The gateway has no `db` component — it owns no tables.

> `auth-service` sets `management.health.rabbit.enabled: false`, so its health
> deliberately ignores RabbitMQ. That was a pre-existing decision: auth can
> serve logins whether or not the broker is up. Remove that line if you want
> the broker to affect its status.

### Service info

```bash
curl http://localhost:8080/actuator/info
```

```json
{
  "app": {
    "name": "api-gateway",
    "description": "Sole public entry point: routing, edge JWT authentication and rate limiting. No database.",
    "version": "0.0.1-SNAPSHOT"
  }
}
```

The version is filtered in from the POM at build time via `@project.version@`,
so it cannot drift from the actual build.

---

## Important REST endpoints

Ports below are for calling a service **directly**. In normal use everything
goes through the gateway on 8080, which strips the `/api` prefix.

### auth-service (8081)

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/register` | public | 201 on success, 409 if the email exists |
| POST | `/auth/login` | public | 200 + JWT, 401 on bad credentials |
| GET | `/auth/me` | **JWT** | Profile plus per-project roles |

```bash
curl -X POST http://localhost:8081/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@demo.devpulse","password":"password123"}'

curl http://localhost:8081/auth/me -H "Authorization: Bearer <token>"
```

### integration-service (8082)

| Method | Path | Notes |
|---|---|---|
| POST | `/webhooks/github` | Signature-validated GitHub webhook |
| POST | `/webhooks/jira` | Signature-validated Jira webhook |
| POST | `/webhooks/test-high-risk-alert` | Publishes a test `alert.pr_high_risk` event |

### notification-service (8084)

| Method | Path | Notes |
|---|---|---|
| GET | `/alerts/rules?companyId=1` | List alert rules |
| GET | `/alerts/rules/{id}` | One rule, 404 if absent |
| POST | `/alerts/rules` | Create a rule |
| DELETE | `/alerts/rules/{id}` | Delete a rule |

### Through the gateway (8080)

`/api/auth/**`, `/api/users/**` → auth-service ·
`/api/webhooks/**`, `/api/integrations/**` → integration-service ·
`/api/alerts/**`, `/api/notifications/**` → notification-service

Routes for metrics-service and analytics-service are still commented out in
`api-gateway/src/main/resources/application.yml`.

```bash
# Public route — no token needed
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@demo.devpulse","password":"password123"}'

# Protected route without a token -> 401
curl -i http://localhost:8080/api/alerts/rules
```

> Gateway global filters run only for **matched** routes, so `/actuator/**` on
> the gateway is never intercepted by the JWT filter, and a path with no route
> returns 404 before authentication is ever considered.

---

## What each test layer covers

| Layer | Annotation | Infrastructure |
|---|---|---|
| Unit | plain JUnit + Mockito | none |
| Web slice | `@WebMvcTest` | none |
| Context smoke | `@SpringBootTest` | none (H2, listeners off) |
| Endpoint | `@SpringBootTest` + `@AutoConfigureMockMvc` | none (service mocked) |
| Gateway edge | `@SpringBootTest(RANDOM_PORT)` + `WebTestClient` | none |

The context smoke test is the cheapest high-value test: it catches a missing
bean, a bad component scan, a property with no default, or a circular
dependency — the failures that otherwise only surface at deploy time.
