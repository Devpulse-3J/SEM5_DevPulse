# DevPulse — Backend Connect Report

**Generated:** 2026-08-12 · **Branch:** `didula-part-2` · **Database:** Supabase (pooler, `ap-northeast-1`)

Everything below was **executed against the running Docker stack**, not inferred. HTTP codes shown
are the codes actually returned.

---

## 1. Verdict: can the frontend connect?

**Yes — for authentication.** Register, login, `/me`, JWT enforcement, CORS and rate limiting are all
working through the gateway on `http://localhost:8080`. Kalhara can wire the login screen today.

**No — for dashboard data.** There are no metrics endpoints, and no way to create a project. See
§6 for exactly what is missing.

---

## 2. What is running

| Container | Port | Status | Notes |
|---|---|---|---|
| `devpulse-api-gateway` | 8080 | ✅ healthy | **Sole public entry.** Point the frontend here. |
| `devpulse-auth-service` | 8081 | ✅ healthy | Talks to Supabase |
| `devpulse-integration-service` | 8082 | ✅ healthy | GitHub/Jira webhooks → RabbitMQ |
| `devpulse-notification-service` | 8084 | ✅ healthy | Consumes events, writes alerts |
| `devpulse-redis` | 6379 | ✅ healthy | Backs the rate limiter |
| `devpulse-rabbitmq` | 5672 / 15672 | ✅ healthy | Event bus |
| `devpulse-metrics-service` | 8083 | ⛔ not started | Empty shell — no controllers exist |
| `devpulse-analytics-service` | 8000 | ⛔ not started | Only `/health` exists |
| `devpulse-postgres` / `devpulse-flyway` | — | ⛔ **intentionally not used** | Supabase replaces them |

> **Never start `postgres` or `flyway`.** The DB is Supabase. The local Postgres container also
> collides with your host Postgres on port 5432 and will fail to bind.

---

## 3. Commands — bring the stack up

Run from `infrastructure/docker/`:

```bash
cd ~/projects/sem\ 5/infrastructure/docker
```

### Start everything

```bash
docker compose -f docker-compose.yml -f docker-compose.supabase.yml \
  up -d --build redis rabbitmq auth-service api-gateway integration-service notification-service
```

**Both `-f` flags are mandatory, every single time.** The second file is what repoints the services
at Supabase and removes the dead `postgres`/`flyway` dependencies. Omit it and the services hang
forever waiting on a database container that cannot start.

### Check status

```bash
docker compose -f docker-compose.yml -f docker-compose.supabase.yml ps

for p in 8080 8081 8082 8084; do
  curl -s -o /dev/null -w "port $p -> %{http_code}\n" http://localhost:$p/actuator/health
done
```

All four must print `200`.

### Logs

```bash
# follow everything
docker compose -f docker-compose.yml -f docker-compose.supabase.yml logs -f

# one service
docker logs -f devpulse-api-gateway
docker logs -f devpulse-auth-service
```

### Stop / restart

```bash
# stop, keep containers
docker compose -f docker-compose.yml -f docker-compose.supabase.yml stop

# tear down completely
docker compose -f docker-compose.yml -f docker-compose.supabase.yml down

# rebuild one service after a code change
docker compose -f docker-compose.yml -f docker-compose.supabase.yml up -d --build auth-service
```

---

## 4. Links to open in Chrome

| URL | What you get |
|---|---|
| http://localhost:15672 | **RabbitMQ management UI** — the useful one. Watch `notification.events` fill up. Login `devpulse` / the `RABBITMQ_PASSWORD` value in `infrastructure/docker/.env` |
| http://localhost:8080/actuator/health | Gateway health — should show `{"status":"UP"}` |
| http://localhost:8081/actuator/health | Auth health (includes the Supabase connection) |
| http://localhost:8082/actuator/health | Integration health |
| http://localhost:8084/actuator/health | Notification health |
| https://supabase.com/dashboard | Your Supabase project — table editor to inspect `users`, `alerts`, etc. |

### Links that do **not** work (don't waste time)

- **No Swagger / OpenAPI UI on any Java service.** `springdoc` is not in any POM, so
  `/swagger-ui.html` returns 404 everywhere. Use the curl commands in §5.
- `http://localhost:8000/docs` — FastAPI docs would work, but analytics-service isn't running.
- Browsing `http://localhost:8080/api/auth/me` directly in Chrome returns **401** — the browser
  sends no `Authorization` header. That is correct behaviour, not a bug.

---

## 5. Verified endpoints (actual results)

All through the gateway at `http://localhost:8080`.

### Auth — works

```bash
EM="test+$(date +%s)@devpulse.io"

curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EM\",\"password\":\"Passw0rd123\",\"fullName\":\"Test User\",\"companyName\":\"Test Org\",\"isCompany\":true}"

TOK=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EM\",\"password\":\"Passw0rd123\"}" \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -w '\nHTTP %{http_code}\n' http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOK"
```

| Call | Result |
|---|---|
| `POST /api/auth/register` | **201** + `accessToken`, `userId`, `systemRole` |
| `POST /api/auth/login` | **200** + same payload |
| `GET /api/auth/me` (with token) | **200** + `companyId`, `companyName`, `projectRoles: []` |
| `GET /api/auth/me` (no token) | **401** `{"status":401,"error":"Unauthorized","path":...}` |
| `GET /api/auth/me` (garbage token) | **401** |
| `GET /api/auth/me` (forged `X-User-Id: 1`) | **401** — header stripped, not honoured ✅ |

### Other routed endpoints — work

| Call | Result |
|---|---|
| `GET /api/alerts/rules` (with token) | **200** `[]` |
| `POST /api/webhooks/test-high-risk-alert` | **200** — publishes to RabbitMQ |

### Async pipeline — works end to end

Firing `POST /api/webhooks/test-high-risk-alert` produces, in notification-service:

```
received event [AlertPrHighRiskEvent] ... eventType: alert.pr_high_risk
Processing HIGH RISK PR Alert for PR ID: 105, Risk Score: 0.92
Delivering Slack Notification to '#dev-alerts'  (webhook URL not configured → simulated)
Sending Email Notification to 'alerts@devpulse.com'
```

gateway → integration → RabbitMQ → notification → DB + Slack + email. **This is a demoable
end-to-end workflow that needs no ML.**

### CORS — correct

Preflight from `http://localhost:3000` returns `Access-Control-Allow-Origin: http://localhost:3000`,
`Allow-Credentials: true`, all methods. Critically, the **401 responses also carry the CORS header**,
so the browser shows the real error instead of an opaque CORS failure.

Only `http://localhost:3000` is allowed. A frontend on any other port will be blocked.

### Rate limiting — active

10 req/s sustained, burst 20, keyed per user (`request_rate_limiter.{user:<id>}` in Redis).
A 60-request parallel burst returned 30×200 / 30×429. **The frontend must handle 429.**

---

## 6. What the frontend CANNOT do yet

| Gap | Impact | Owner |
|---|---|---|
| **No metrics endpoints** | `metrics-service` has an entrypoint and nothing else — zero controllers. The DORA dashboard has no data source at all. | Kalhara |
| **No way to create a project** | `projects` table is **empty (0 rows)** and auth-service has no `ProjectController` — only register/login/me. `projectRoles` will be `[]` for every user, and per-project RBAC can't be exercised. | Kalhara |
| **No `/api/auth/refresh`** | The gateway treats it as a public path, but the endpoint doesn't exist. Tokens expire after **3600s** with no recovery — the user is silently logged out after an hour. Don't build refresh logic against it yet. | Kalhara |
| **No `/api/users/**` handler** | The gateway routes this path, but auth-service has no `UserController`. Use `/api/auth/me`. | Kalhara |
| **No analytics/predictions** | `analytics-service` is an empty FastAPI app. `alert.pr_high_risk` is currently faked by `integration-service`'s `/webhooks/test-high-risk-alert`. | Didula |

### Known non-blocking issues

- **`alerts.project_id` FK violation.** Incoming alerts reference `projectId: 1`, but `projects` is
  empty, so the insert fails and the listener falls back to a null project. It recovers, but every
  alert is written without project context until projects exist.
- **Alert rules are not evaluated.** `AlertRuleController`/`Service`/`Repository` all exist, but
  `NotificationEventListener` doesn't consult them — it alerts on every event and hardcodes
  `#dev-alerts`. The `alert_rules` table is currently decorative.
- **Slack/email are simulated.** `SLACK_WEBHOOK_URL` is a placeholder, so delivery is logged, not sent.
- ~~**JWT algorithm differs by run mode.**~~ **Fixed.** Docker and both `.env.local` files now carry
  the same 33-char secret, so every mode issues **HS256** (matching CLAUDE.md) and a token stays
  valid across a Docker↔native switch. Verified: `{"alg":"HS256"}`, `/api/auth/me` → 200.

  For future reference — the algorithm is chosen by secret *length*, not configuration.
  `Keys.hmacShaKeyFor()` picks HS256 for 32–47 bytes, HS384 for 48–63, HS512 for 64+. The code
  never names an algorithm, so changing the secret's length silently changes the algorithm.

---

## 7. Current data in Supabase

| Table | Rows |
|---|---|
| `companies` | 4 |
| `users` | 7 |
| `projects` | **0** |
| `alerts` | 2 |
| `alert_rules` | 0 |

All 17 tables exist; the schema is fully migrated. Users came from test registrations.

---

## 8. Fixes applied to get this working

1. **`AuthServiceImpl.java` did not compile** — `OffsetDateTime` used in three places, never
   imported. Added `import java.time.OffsetDateTime;`.
2. **Gateway routed to itself in Docker** — routes default to `${AUTH_SERVICE_URI:http://localhost:8081}`,
   and compose never set it, so `localhost` resolved to the gateway's own container
   (`Connection refused: localhost/127.0.0.1:8081`, surfacing as a 500 on register). Added
   `AUTH_SERVICE_URI` and the four sibling URIs to `docker-compose.yml`.
3. **PgBouncer prepared-statement collisions** — Supabase port 6543 is the transaction pooler, which
   recycles backend connections between transactions, so cached server-side statements collide
   (`prepared statement "S_1" already exists` → `current transaction is aborted` → 500). Only
   appeared under concurrency: a 60-request burst produced 2×500. Fixed by appending
   `?prepareThreshold=0&preparedStatementCacheQueries=0` to the JDBC URL. **Keep these parameters.**
4. **RabbitMQ ACCESS_REFUSED on 5672** — auth-service's `RABBITMQ_URL` had no credentials, and
   `RABBITMQ_DEFAULT_USER=devpulse` *replaces* the built-in `guest` account rather than adding to it.
   Copied the credentialed URL from integration-service.
5. **Unquoted `&` in `.env.local`** — bash reads it as a background operator when the file is sourced,
   silently truncating the URL. Any Supabase URL with query parameters **must be quoted**.
6. **New `docker-compose.supabase.yml`** — repoints all DB services at Supabase and uses `!override`
   on `depends_on` to drop postgres/flyway. (Your IDE flags `!override` as an unknown tag; that's the
   YAML schema being out of date, not an error — `docker compose config` accepts it.)

Backups: `infrastructure/docker/.env.bak`, `backend/auth-service/.env.local.bak`.

---

## 9. Recommended next steps

1. **Kalhara** — wire the frontend login against `http://localhost:8080/api/auth/*`. It works now.
2. **Kalhara** — build `metrics-service` controllers and a project-creation endpoint. These are the
   critical path for anything dashboard-shaped; nothing else unblocks them.
3. **Umaya** — make `NotificationEventListener` actually read `alert_rules`.
4. **Didula** — build `analytics-service`, then delete the faked
   `/webhooks/test-high-risk-alert` endpoint from integration-service.
